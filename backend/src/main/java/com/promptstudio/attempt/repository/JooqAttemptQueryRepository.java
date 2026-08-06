package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.domain.LlmUsageSummary;
import com.promptstudio.attempt.domain.LlmUsageTotals;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.problem.domain.ProblemFile;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record9;
import org.jooq.Result;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.promptstudio.attempt.repository.AttemptTables.ATTEMPT;
import static com.promptstudio.attempt.repository.AttemptTables.ATTEMPT_FILE;
import static com.promptstudio.attempt.repository.AttemptTables.ATTEMPT_LLM_CALL;
import static com.promptstudio.attempt.repository.AttemptTables.ATTEMPT_TURN;
import static com.promptstudio.attempt.repository.AttemptTables.CALL_ATTEMPT_ID;
import static com.promptstudio.attempt.repository.AttemptTables.CALL_CACHED_INPUT_TOKENS;
import static com.promptstudio.attempt.repository.AttemptTables.CALL_COST;
import static com.promptstudio.attempt.repository.AttemptTables.CALL_INPUT_TOKENS;
import static com.promptstudio.attempt.repository.AttemptTables.CALL_LATENCY_MS;
import static com.promptstudio.attempt.repository.AttemptTables.CALL_MODEL;
import static com.promptstudio.attempt.repository.AttemptTables.CALL_OUTPUT_TOKENS;
import static com.promptstudio.attempt.repository.AttemptTables.CALL_REASONING_TOKENS;
import static com.promptstudio.attempt.repository.AttemptTables.CALL_TURN_ORDINAL;
import static com.promptstudio.attempt.repository.AttemptTables.CHANGE_CONTENT;
import static com.promptstudio.attempt.repository.AttemptTables.CHANGE_ORDINAL;
import static com.promptstudio.attempt.repository.AttemptTables.CHANGE_PATH;
import static com.promptstudio.attempt.repository.AttemptTables.CHANGE_TURN_ID;
import static com.promptstudio.attempt.repository.AttemptTables.CHANGE_TYPE;
import static com.promptstudio.attempt.repository.AttemptTables.FEEDBACK;
import static com.promptstudio.attempt.repository.AttemptTables.FILE_ATTEMPT_ID;
import static com.promptstudio.attempt.repository.AttemptTables.FILE_CONTENT;
import static com.promptstudio.attempt.repository.AttemptTables.FILE_ORDINAL;
import static com.promptstudio.attempt.repository.AttemptTables.FILE_PATH;
import static com.promptstudio.attempt.repository.AttemptTables.ID;
import static com.promptstudio.attempt.repository.AttemptTables.GUEST_SESSION_ID;
import static com.promptstudio.attempt.repository.AttemptTables.PATTERN_FEEDBACK;
import static com.promptstudio.attempt.repository.AttemptTables.PROBLEM_ID;
import static com.promptstudio.attempt.repository.AttemptTables.STATUS;
import static com.promptstudio.attempt.repository.AttemptTables.USERS;
import static com.promptstudio.attempt.repository.AttemptTables.USERS_ID;
import static com.promptstudio.attempt.repository.AttemptTables.USERS_NICKNAME;
import static com.promptstudio.attempt.repository.AttemptTables.USER_ID;
import static com.promptstudio.attempt.repository.AttemptTables.TOOL_CALL_ORDINAL;
import static com.promptstudio.attempt.repository.AttemptTables.TOOL_CALL_PATH;
import static com.promptstudio.attempt.repository.AttemptTables.TOOL_CALL_TOOL;
import static com.promptstudio.attempt.repository.AttemptTables.TOOL_CALL_TURN_ID;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_AI_SUMMARY;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_ATTEMPT_ID;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_FEEDBACK;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_FILE_CHANGE;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_ID;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_ORDINAL;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_PATTERN_FEEDBACK;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_TOOL_CALL;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_USER_PROMPT;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;

@Repository
public class JooqAttemptQueryRepository implements AttemptQueryRepository {

    // 집계 컬럼. 자리 번호로 꺼내면 컬럼이 하나 끼어들 때 조용히 어긋나므로 이 필드로 꺼낸다.
    // 별칭에 sum_ 접두사를 붙여 attempt_llm_call의 같은 이름 원본 컬럼과 겹치지 않게 한다.
    private static final Field<Integer> ROUNDS = count().as("rounds");
    private static final Field<BigDecimal> INPUT_TOKENS = sum(CALL_INPUT_TOKENS).as("sum_input_tokens");
    // 캐시 적중분은 input에 포함돼 있어 행 단위로 빼야 한다. 합계끼리 빼면 input을 모르는 행의
    // cached가 남아 음수 쪽으로 새어 나간다.
    private static final Field<BigDecimal> UNCACHED_INPUT_TOKENS =
            sum(CALL_INPUT_TOKENS.minus(coalesce(CALL_CACHED_INPUT_TOKENS, 0L))).as("sum_uncached_input_tokens");
    private static final Field<BigDecimal> CACHED_INPUT_TOKENS =
            sum(CALL_CACHED_INPUT_TOKENS).as("sum_cached_input_tokens");
    private static final Field<BigDecimal> OUTPUT_TOKENS = sum(CALL_OUTPUT_TOKENS).as("sum_output_tokens");
    private static final Field<BigDecimal> REASONING_TOKENS =
            sum(CALL_REASONING_TOKENS).as("sum_reasoning_tokens");
    private static final Field<BigDecimal> LATENCY_MS = sum(CALL_LATENCY_MS).as("sum_latency_ms");
    private static final Field<BigDecimal> COST = sum(CALL_COST).as("sum_cost");
    private static final Field<String> MODEL = max(CALL_MODEL).as("max_model");

    private final DSLContext dsl;

    public JooqAttemptQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttemptView> findById(Long id) {
        return findByCondition(ID.eq(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttemptView> findByIdAndUserId(Long id, Long userId) {
        return findByCondition(ID.eq(id).and(USER_ID.eq(userId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttemptView> findByIdAndGuestSessionId(Long id, UUID guestSessionId) {
        return findByCondition(ID.eq(id).and(GUEST_SESSION_ID.eq(guestSessionId)));
    }

    /**
     * multiset 컬럼은 지역 변수로 뽑아야 {@code record.get(...)}으로 되꺼낼 수 있다. 자리 번호로 꺼내면
     * 컬럼이 하나 끼어들 때 조용히 어긋난다.
     *
     * <p>이름 없는 multiset은 모두 같은 이름을 갖는다 — 한 SELECT에 여럿이 들어가므로 별칭으로 갈라 둔다.
     */
    private Optional<AttemptView> findByCondition(org.jooq.Condition condition) {
        Field<List<ProblemFile>> baseFiles = baseFilesField();
        Field<List<AttemptView.TurnView>> turns = turnsField();
        Field<LlmUsageTotals> usage = usageTotalsField();

        return dsl.select(
                        ID, PROBLEM_ID, USER_ID, GUEST_SESSION_ID, USERS_NICKNAME,
                        baseFiles, turns, STATUS, FEEDBACK, PATTERN_FEEDBACK, usage)
                .from(ATTEMPT)
                // 주인의 표시 이름을 같은 왕복에서 얻는다. 게스트 소유는 매칭되는 행이 없어 닉네임이 null이다.
                .leftJoin(USERS).on(USERS_ID.eq(USER_ID))
                .where(condition)
                .fetchOptional(record -> AttemptView.reconstruct(
                        record.get(ID),
                        record.get(PROBLEM_ID),
                        owner(record.get(USER_ID), record.get(GUEST_SESSION_ID)),
                        record.get(USERS_NICKNAME),
                        record.get(baseFiles),
                        record.get(turns),
                        AttemptStatus.valueOf(record.get(STATUS)),
                        record.get(FEEDBACK),
                        record.get(PATTERN_FEEDBACK),
                        record.get(usage)
                ));
    }

    /**
     * 소유자 컬럼이 생기기 전 행은 둘 다 비어 있다 — AttemptOwner가 그 조합을 거부하므로 null로 남긴다.
     */
    private AttemptOwner owner(Long userId, UUID guestSessionId) {
        if (userId == null && guestSessionId == null) {
            return null;
        }

        return new AttemptOwner(userId, guestSessionId);
    }

    private Field<List<AttemptView.TurnView>> turnsField() {
        Field<List<FileChange>> changes = multiset(
                select(CHANGE_PATH, CHANGE_TYPE, CHANGE_CONTENT)
                        .from(TURN_FILE_CHANGE)
                        .where(CHANGE_TURN_ID.eq(TURN_ID))
                        .orderBy(CHANGE_ORDINAL)
        ).convertFrom(result -> result.map(record -> new FileChange(
                record.get(CHANGE_PATH),
                FileChange.ChangeType.valueOf(record.get(CHANGE_TYPE)),
                record.get(CHANGE_CONTENT)))
        ).as("changes");

        Field<List<ToolCallEntry>> toolCalls = multiset(
                select(TOOL_CALL_TOOL, TOOL_CALL_PATH)
                        .from(TURN_TOOL_CALL)
                        .where(TOOL_CALL_TURN_ID.eq(TURN_ID))
                        .orderBy(TOOL_CALL_ORDINAL)
        ).convertFrom(result -> result.map(record ->
                new ToolCallEntry(record.get(TOOL_CALL_TOOL), record.get(TOOL_CALL_PATH)))
        ).as("tool_calls");

        Field<LlmUsageSummary> usage = turnUsageField();

        return multiset(
                select(
                        TURN_USER_PROMPT,
                        TURN_AI_SUMMARY,
                        changes,
                        toolCalls,
                        TURN_FEEDBACK,
                        TURN_PATTERN_FEEDBACK,
                        usage
                )
                        .from(ATTEMPT_TURN)
                        .where(TURN_ATTEMPT_ID.eq(ID))
                        .orderBy(TURN_ORDINAL)
        ).convertFrom(result -> result.map(record -> new AttemptView.TurnView(
                record.get(TURN_USER_PROMPT),
                record.get(TURN_AI_SUMMARY),
                record.get(changes),
                record.get(toolCalls),
                record.get(TURN_FEEDBACK),
                record.get(TURN_PATTERN_FEEDBACK),
                record.get(usage)))
        ).as("turns");
    }

    /**
     * 그 턴에 속한 호출 행의 합계. 턴 연결은 turn_id가 아니라 (attempt_id, ordinal)이다.
     */
    private Field<LlmUsageSummary> turnUsageField() {
        return usageAggregate(CALL_ATTEMPT_ID.eq(TURN_ATTEMPT_ID).and(CALL_TURN_ORDINAL.eq(TURN_ORDINAL)))
                .convertFrom(result -> {
                    Record row = result.get(0);
                    int rounds = row.get(ROUNDS);

                    if (rounds == 0) {
                        return null;
                    }

                    return new LlmUsageSummary(
                            sumAsLong(row, INPUT_TOKENS),
                            sumAsLong(row, UNCACHED_INPUT_TOKENS),
                            sumAsLong(row, CACHED_INPUT_TOKENS),
                            sumAsLong(row, OUTPUT_TOKENS),
                            sumAsLong(row, REASONING_TOKENS),
                            sumAsLong(row, LATENCY_MS),
                            row.get(COST),
                            row.get(MODEL),
                            rounds
                    );
                }).as("turn_usage");
    }

    /**
     * 어템프트의 턴 합계 — 턴에 속하지 않는 호출(피드백 생성, 실패 flush)은 뺀다.
     *
     * <p>이 수치는 사용자가 자기 프롬프트의 효율을 보는 지표이고, 빠지는 호출은 서비스가 부담하는
     * 비용이다. 그래서 턴을 다 더하면 정확히 이 총계가 나온다. 실제 과금 총액이 필요하면 원본
     * 행(attempt_llm_call)을 직접 집계한다.
     */
    private Field<LlmUsageTotals> usageTotalsField() {
        return usageAggregate(CALL_ATTEMPT_ID.eq(ID).and(CALL_TURN_ORDINAL.isNotNull()))
                .convertFrom(result -> {
                    Record row = result.get(0);
                    int rounds = row.get(ROUNDS);

                    if (rounds == 0) {
                        return null;
                    }

                    return new LlmUsageTotals(
                            sumAsLong(row, INPUT_TOKENS),
                            sumAsLong(row, UNCACHED_INPUT_TOKENS),
                            sumAsLong(row, CACHED_INPUT_TOKENS),
                            sumAsLong(row, OUTPUT_TOKENS),
                            sumAsLong(row, REASONING_TOKENS),
                            sumAsLong(row, LATENCY_MS),
                            row.get(COST),
                            rounds
                    );
                }).as("usage_totals");
    }

    /**
     * GROUP BY가 없는 집계라 항상 정확히 한 행이 나온다 — 행이 없는 경우는 count 0으로 구분한다.
     *
     * <p>SUM은 null 항을 건너뛴다. 제공자가 사용량을 주지 않은 호출은 그 항목의 합계에서 빠지고,
     * 0으로 세지 않는다 — 모르는 값을 0으로 적으면 합계가 거짓말이 되기 때문이다.
     */
    private Field<Result<Record9<
            Integer, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal, String>>>
    usageAggregate(Condition correlation) {
        return multiset(
                select(
                        ROUNDS,
                        INPUT_TOKENS,
                        UNCACHED_INPUT_TOKENS,
                        CACHED_INPUT_TOKENS,
                        OUTPUT_TOKENS,
                        REASONING_TOKENS,
                        LATENCY_MS,
                        COST,
                        MODEL
                )
                        .from(ATTEMPT_LLM_CALL)
                        .where(correlation)
        );
    }

    private Long sumAsLong(Record row, Field<BigDecimal> column) {
        BigDecimal value = row.get(column);

        return value == null ? null : value.longValue();
    }

    private Field<List<ProblemFile>> baseFilesField() {
        return multiset(
                select(FILE_PATH, FILE_CONTENT)
                        .from(ATTEMPT_FILE)
                        .where(FILE_ATTEMPT_ID.eq(ID))
                        .orderBy(FILE_ORDINAL)
        ).convertFrom(result -> result.map(record ->
                new ProblemFile(record.get(FILE_PATH), record.get(FILE_CONTENT)))
        ).as("base_files");
    }
}
