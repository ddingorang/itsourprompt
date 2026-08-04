package com.promptstudio.ranking.repository;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.pricing.LlmPricingProperties;
import com.promptstudio.ranking.domain.RankedPage;
import com.promptstudio.ranking.domain.RankingEntry;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.SelectOnConditionStep;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.promptstudio.pricing.repository.ModelPriceTables.CACHED_INPUT;
import static com.promptstudio.pricing.repository.ModelPriceTables.INPUT;
import static com.promptstudio.pricing.repository.ModelPriceTables.MODEL;
import static com.promptstudio.pricing.repository.ModelPriceTables.MODEL_PRICE;
import static com.promptstudio.pricing.repository.ModelPriceTables.OUTPUT;
import static org.jooq.impl.DSL.boolAnd;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.rank;
import static org.jooq.impl.DSL.round;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.table;

/**
 * 랭킹 조회. 저장된 {@code attempt_llm_call.cost}를 쓰지 않고 {@code model_price}의 현재 단가로
 * 전원을 다시 잰다 — 저장된 비용은 쓰기 시점 단가로 박힌 지출 기록이라, 단가가 한 번 바뀌면
 * 과거 제출과 새 제출이 서로 다른 자로 재어진다.
 *
 * <p>쿼리는 파생 테이블 {@code ranked} 한 겹이다. PostgreSQL은 윈도우 함수를 GROUP BY·HAVING
 * <b>다음에</b> 평가하므로 자격 판정·집계·등수를 한 SELECT에서 끝낼 수 있다. 겹을 하나 더 두면
 * 같은 열을 한 번 더 선언해야 하고, 그 짝은 컴파일러가 맞춰주지 않는다. 바깥 SELECT가 남아 있는
 * 이유는 하나뿐이다 — 윈도우 함수 결과({@code seq})로는 자기 SELECT의 WHERE에서 거를 수 없다.
 */
@Repository
public class JooqRankingQueryRepository implements RankingQueryRepository {

    private static final Table<?> ATTEMPT = table(name("attempt"));
    private static final Field<Long> ATTEMPT_ID = field(name("attempt", "id"), SQLDataType.BIGINT);
    private static final Field<Long> ATTEMPT_PROBLEM_ID = field(name("attempt", "problem_id"), SQLDataType.BIGINT);
    private static final Field<Long> ATTEMPT_USER_ID = field(name("attempt", "user_id"), SQLDataType.BIGINT);
    private static final Field<UUID> ATTEMPT_GUEST_SESSION_ID =
            field(name("attempt", "guest_session_id"), SQLDataType.UUID);
    private static final Field<String> ATTEMPT_STATUS = field(name("attempt", "status"), SQLDataType.VARCHAR);
    private static final Field<Instant> ATTEMPT_SUBMITTED_AT =
            field(name("attempt", "submitted_at"), SQLDataType.INSTANT);

    private static final Table<?> ATTEMPT_TURN = table(name("attempt_turn"));
    private static final Field<Long> TURN_ATTEMPT_ID = field(name("attempt_turn", "attempt_id"), SQLDataType.BIGINT);
    private static final Field<Integer> TURN_ORDINAL = field(name("attempt_turn", "ordinal"), SQLDataType.INTEGER);

    private static final Table<?> ATTEMPT_LLM_CALL = table(name("attempt_llm_call"));
    private static final Field<Long> CALL_ATTEMPT_ID =
            field(name("attempt_llm_call", "attempt_id"), SQLDataType.BIGINT);
    private static final Field<Integer> CALL_TURN_ORDINAL =
            field(name("attempt_llm_call", "turn_ordinal"), SQLDataType.INTEGER);
    private static final Field<String> CALL_MODEL = field(name("attempt_llm_call", "model"), SQLDataType.VARCHAR);
    private static final Field<Long> CALL_INPUT_TOKENS =
            field(name("attempt_llm_call", "input_tokens"), SQLDataType.BIGINT);
    private static final Field<Long> CALL_OUTPUT_TOKENS =
            field(name("attempt_llm_call", "output_tokens"), SQLDataType.BIGINT);
    private static final Field<Long> CALL_CACHED_INPUT_TOKENS =
            field(name("attempt_llm_call", "cached_input_tokens"), SQLDataType.BIGINT);

    private static final Table<?> CODE_RUN = table(name("code_run"));
    private static final Field<Long> RUN_ATTEMPT_ID = field(name("code_run", "attempt_id"), SQLDataType.BIGINT);
    private static final Field<Integer> RUN_TURN_ORDINAL =
            field(name("code_run", "turn_ordinal"), SQLDataType.INTEGER);
    private static final Field<String> RUN_STATUS = field(name("code_run", "status"), SQLDataType.VARCHAR);

    private static final Table<?> USERS = table(name("users"));
    private static final Field<Long> USER_ID = field(name("users", "id"), SQLDataType.BIGINT);
    private static final Field<String> USER_NICKNAME = field(name("users", "nickname"), SQLDataType.VARCHAR);

    /**
     * 단가를 곱해 100만으로 나누기 전의 값. 별칭을 붙이지 않은 원식이라 윈도우 ORDER BY가 이걸 쓴다 —
     * SQL은 같은 SELECT의 별칭을 윈도우 절에서 참조하지 못한다.
     *
     * <p>곱셈을 다 끝낸 뒤 마지막에 한 번만 나눈다. 행마다 나누면 반올림 오차가 쌓인다.
     */
    private static final Field<BigDecimal> COST_SCALED_EXPR = sum(
            INPUT.mul(CALL_INPUT_TOKENS.minus(coalesce(CALL_CACHED_INPUT_TOKENS, 0L)))
                    .plus(coalesce(CACHED_INPUT, INPUT).mul(coalesce(CALL_CACHED_INPUT_TOKENS, 0L)))
                    .plus(OUTPUT.mul(CALL_OUTPUT_TOKENS))
    );

    private static final List<SelectFieldOrAsterisk> RANKED_FIELDS = List.of(
            ATTEMPT_ID, ATTEMPT_USER_ID, ATTEMPT_GUEST_SESSION_ID, ATTEMPT_SUBMITTED_AT,
            COST_SCALED_EXPR.as("cost_scaled"),
            // 캐시 적중분은 input에 포함돼 있어 행 단위로 빼야 한다(JooqAttemptQueryRepository와 같은 이유).
            sum(CALL_INPUT_TOKENS.minus(coalesce(CALL_CACHED_INPUT_TOKENS, 0L))).as("uncached_input_tokens"),
            sum(coalesce(CALL_CACHED_INPUT_TOKENS, 0L)).as("cached_input_tokens"),
            sum(CALL_OUTPUT_TOKENS).as("output_tokens"),
            field(select(count()).from(ATTEMPT_TURN).where(TURN_ATTEMPT_ID.eq(ATTEMPT_ID))).as("turns"),
            count().as("rounds"),
            // 등수는 cost만 본다. 동점은 같은 등수를 받고 다음 등수는 건너뛴다.
            rank().over().orderBy(COST_SCALED_EXPR).as("rank"),
            // 표에 찍히는 순서. 등수와 달리 동점을 반드시 갈라야 해서 제출 시각과 id까지 본다.
            rowNumber().over()
                    .orderBy(COST_SCALED_EXPR, ATTEMPT_SUBMITTED_AT.asc().nullsLast(), ATTEMPT_ID)
                    .as("seq"),
            // 자격을 갖춘 제출 전체 수. 등수를 매기며 어차피 전 행을 훑으므로 여기서 같이 센다.
            count().over().as("total_count"));

    private static final Field<Long> RANKED_ATTEMPT_ID = field(name("ranked", "id"), SQLDataType.BIGINT);
    private static final Field<Long> RANKED_USER_ID = field(name("ranked", "user_id"), SQLDataType.BIGINT);
    private static final Field<UUID> RANKED_GUEST_SESSION_ID =
            field(name("ranked", "guest_session_id"), SQLDataType.UUID);
    private static final Field<Instant> RANKED_SUBMITTED_AT =
            field(name("ranked", "submitted_at"), SQLDataType.INSTANT);
    private static final Field<BigDecimal> RANKED_COST_SCALED =
            field(name("ranked", "cost_scaled"), SQLDataType.DECIMAL);
    private static final Field<BigDecimal> RANKED_UNCACHED_INPUT_TOKENS =
            field(name("ranked", "uncached_input_tokens"), SQLDataType.DECIMAL);
    private static final Field<BigDecimal> RANKED_CACHED_INPUT_TOKENS =
            field(name("ranked", "cached_input_tokens"), SQLDataType.DECIMAL);
    private static final Field<BigDecimal> RANKED_OUTPUT_TOKENS =
            field(name("ranked", "output_tokens"), SQLDataType.DECIMAL);
    private static final Field<Integer> RANKED_TURNS = field(name("ranked", "turns"), SQLDataType.INTEGER);
    private static final Field<Integer> RANKED_ROUNDS = field(name("ranked", "rounds"), SQLDataType.INTEGER);
    private static final Field<Integer> RANKED_RANK = field(name("ranked", "rank"), SQLDataType.INTEGER);
    private static final Field<Integer> RANKED_SEQ = field(name("ranked", "seq"), SQLDataType.INTEGER);
    private static final Field<Integer> RANKED_TOTAL_COUNT =
            field(name("ranked", "total_count"), SQLDataType.INTEGER);

    private static final Field<BigDecimal> COST = round(
            RANKED_COST_SCALED.div(LlmPricingProperties.TOKENS_PER_PRICE_UNIT),
            LlmPricingProperties.COST_SCALE).as("cost");

    private static final List<SelectFieldOrAsterisk> ENTRY_FIELDS = List.of(
            RANKED_RANK, RANKED_ATTEMPT_ID, RANKED_USER_ID, RANKED_GUEST_SESSION_ID, USER_NICKNAME,
            COST, RANKED_UNCACHED_INPUT_TOKENS, RANKED_CACHED_INPUT_TOKENS, RANKED_OUTPUT_TOKENS,
            RANKED_TURNS, RANKED_ROUNDS, RANKED_SUBMITTED_AT, RANKED_TOTAL_COUNT);

    private final DSLContext dsl;

    public JooqRankingQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public RankedPage findTop(Long problemId, int limit) {
        Result<Record> rows = selectEntries(problemId)
                .where(RANKED_SEQ.le(limit))
                .orderBy(RANKED_SEQ)
                .fetch();

        // 전체 수는 등수를 매기며 이미 세어 둔 값이라 따로 묻지 않는다. 한 행도 없으면 자격자가 없다는 뜻이다.
        long totalCount = rows.isEmpty() ? 0L : rows.get(0).get(RANKED_TOTAL_COUNT).longValue();

        return new RankedPage(rows.map(JooqRankingQueryRepository::toEntry), totalCount);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RankingEntry> findBestOf(Long problemId, AttemptOwner owner) {
        Condition ownedBy = owner.isUser()
                ? RANKED_USER_ID.eq(owner.userId())
                : RANKED_GUEST_SESSION_ID.eq(owner.guestSessionId());

        return selectEntries(problemId)
                .where(ownedBy)
                .orderBy(RANKED_RANK, RANKED_SEQ)
                .limit(1)
                .fetchOptional(JooqRankingQueryRepository::toEntry);
    }

    private SelectOnConditionStep<Record> selectEntries(Long problemId) {
        return dsl.select(ENTRY_FIELDS)
                .from(ranked(problemId))
                .leftJoin(USERS).on(USER_ID.eq(RANKED_USER_ID));
    }

    /**
     * 자격을 갖춘 제출과 그 집계, 그리고 등수.
     *
     * <p>자격은 넷이다. 제출 완료이고, 마지막 턴의 코드가 채점을 통과했고, 턴에 속한 LLM 호출이
     * 하나 이상이고, 그 호출이 전부 비용을 계산할 수 있어야 한다. 셋째 조건은 INNER JOIN이 대신하고
     * (호출이 없으면 그룹 자체가 생기지 않는다) 넷째는 HAVING의 BOOL_AND가 본다 — 호출 하나라도
     * 토큰이나 단가를 모르면 그 어템프트의 비용은 "0"이 아니라 "모름"이라 표에서 빼야 한다.
     */
    private Table<?> ranked(Long problemId) {
        return dsl.select(RANKED_FIELDS)
                .from(ATTEMPT)
                // 턴에 속하지 않는 호출(피드백·실패 마커)은 프롬프트 실력의 결과가 아니라 제외한다.
                .join(ATTEMPT_LLM_CALL).on(CALL_ATTEMPT_ID.eq(ATTEMPT_ID).and(CALL_TURN_ORDINAL.isNotNull()))
                // LEFT JOIN이라 단가가 없는 모델도 그룹에는 들어오고, HAVING이 그 어템프트를 통째로 뺀다.
                .leftJoin(MODEL_PRICE).on(MODEL.eq(CALL_MODEL))
                .where(ATTEMPT_PROBLEM_ID.eq(problemId))
                .and(ATTEMPT_STATUS.eq(AttemptStatus.SUBMITTED.name()))
                // 정답 게이트. 이게 없으면 빈 프롬프트로 아무것도 만들지 않은 제출이 1등을 한다.
                // SUCCEEDED는 컴파일·실행·채점 테스트를 전부 통과했다는 뜻이다(TEST_FAILED가 별도 값이므로).
                .and(exists(selectOne()
                        .from(CODE_RUN)
                        .where(RUN_ATTEMPT_ID.eq(ATTEMPT_ID))
                        .and(RUN_STATUS.eq(CodeRunStatus.SUCCEEDED.name()))
                        .and(RUN_TURN_ORDINAL.eq(select(max(TURN_ORDINAL))
                                .from(ATTEMPT_TURN)
                                .where(TURN_ATTEMPT_ID.eq(ATTEMPT_ID))))))
                .groupBy(ATTEMPT_ID, ATTEMPT_USER_ID, ATTEMPT_GUEST_SESSION_ID, ATTEMPT_SUBMITTED_AT)
                .having(boolAnd(CALL_INPUT_TOKENS.isNotNull()
                        .and(CALL_OUTPUT_TOKENS.isNotNull())
                        .and(MODEL.isNotNull())).isTrue())
                .asTable("ranked");
    }

    private static RankingEntry toEntry(Record record) {
        return new RankingEntry(
                record.get(RANKED_RANK),
                record.get(RANKED_ATTEMPT_ID),
                new AttemptOwner(record.get(RANKED_USER_ID), record.get(RANKED_GUEST_SESSION_ID)),
                record.get(USER_NICKNAME),
                record.get(COST),
                record.get(RANKED_UNCACHED_INPUT_TOKENS).longValue(),
                record.get(RANKED_CACHED_INPUT_TOKENS).longValue(),
                record.get(RANKED_OUTPUT_TOKENS).longValue(),
                record.get(RANKED_TURNS),
                record.get(RANKED_ROUNDS),
                record.get(RANKED_SUBMITTED_AT)
        );
    }
}
