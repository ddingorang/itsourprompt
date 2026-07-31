package com.promptstudio.attempt.repository;

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
import org.jooq.Record7;
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
import static com.promptstudio.attempt.repository.AttemptTables.PROBLEM_ID;
import static com.promptstudio.attempt.repository.AttemptTables.STATUS;
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
import static com.promptstudio.attempt.repository.AttemptTables.TURN_TOOL_CALL;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_USER_PROMPT;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;

@Repository
public class JooqAttemptQueryRepository implements AttemptQueryRepository {

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

    private Optional<AttemptView> findByCondition(org.jooq.Condition condition) {
        return dsl.select(ID, PROBLEM_ID, baseFilesField(), turnsField(), STATUS, FEEDBACK, usageTotalsField())
                .from(ATTEMPT)
                .where(condition)
                .fetchOptional(record -> AttemptView.reconstruct(
                        record.value1(),
                        record.value2(),
                        record.value3(),
                        record.value4(),
                        AttemptStatus.valueOf(record.value5()),
                        record.value6(),
                        record.value7()
                ));
    }

    private Field<List<AttemptView.TurnView>> turnsField() {
        Field<List<FileChange>> changes = multiset(
                select(CHANGE_PATH, CHANGE_TYPE, CHANGE_CONTENT)
                        .from(TURN_FILE_CHANGE)
                        .where(CHANGE_TURN_ID.eq(TURN_ID))
                        .orderBy(CHANGE_ORDINAL)
        ).convertFrom(result -> result.map(record ->
                new FileChange(record.value1(), FileChange.ChangeType.valueOf(record.value2()), record.value3())));

        Field<List<ToolCallEntry>> toolCalls = multiset(
                select(TOOL_CALL_TOOL, TOOL_CALL_PATH)
                        .from(TURN_TOOL_CALL)
                        .where(TOOL_CALL_TURN_ID.eq(TURN_ID))
                        .orderBy(TOOL_CALL_ORDINAL)
        ).convertFrom(result -> result.map(record -> new ToolCallEntry(record.value1(), record.value2())));

        return multiset(
                select(TURN_USER_PROMPT, TURN_AI_SUMMARY, changes, toolCalls, TURN_FEEDBACK, turnUsageField())
                        .from(ATTEMPT_TURN)
                        .where(TURN_ATTEMPT_ID.eq(ID))
                        .orderBy(TURN_ORDINAL)
        ).convertFrom(result -> result.map(record -> new AttemptView.TurnView(
                record.value1(), record.value2(), record.value3(), record.value4(), record.value5(),
                record.value6())));
    }

    /**
     * 그 턴에 속한 호출 행의 합계. 턴 연결은 turn_id가 아니라 (attempt_id, ordinal)이다.
     */
    private Field<LlmUsageSummary> turnUsageField() {
        return usageAggregate(CALL_ATTEMPT_ID.eq(TURN_ATTEMPT_ID).and(CALL_TURN_ORDINAL.eq(TURN_ORDINAL)))
                .convertFrom(result -> {
                    Record row = result.get(0);
                    int rounds = row.get(0, Integer.class);

                    if (rounds == 0) {
                        return null;
                    }

                    return new LlmUsageSummary(
                            tokens(row, 1),
                            tokens(row, 2),
                            tokens(row, 3),
                            tokens(row, 4),
                            row.get(5, BigDecimal.class),
                            row.get(6, String.class),
                            rounds
                    );
                });
    }

    /**
     * 어템프트 전체 총계 — 턴에 속하지 않는 피드백 호출과 실패 flush 행까지 포함한다.
     */
    private Field<LlmUsageTotals> usageTotalsField() {
        return usageAggregate(CALL_ATTEMPT_ID.eq(ID))
                .convertFrom(result -> {
                    Record row = result.get(0);

                    if (row.get(0, Integer.class) == 0) {
                        return null;
                    }

                    return new LlmUsageTotals(
                            tokens(row, 1),
                            tokens(row, 2),
                            tokens(row, 3),
                            tokens(row, 4),
                            row.get(5, BigDecimal.class)
                    );
                });
    }

    /**
     * GROUP BY가 없는 집계라 항상 정확히 한 행이 나온다 — 행이 없는 경우는 count 0으로 구분한다.
     */
    private Field<Result<Record7<Integer, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal, String>>>
    usageAggregate(Condition correlation) {
        return multiset(
                select(
                        count(),
                        sum(CALL_INPUT_TOKENS),
                        sum(CALL_OUTPUT_TOKENS),
                        sum(CALL_CACHED_INPUT_TOKENS),
                        sum(CALL_REASONING_TOKENS),
                        sum(CALL_COST),
                        max(CALL_MODEL)
                )
                        .from(ATTEMPT_LLM_CALL)
                        .where(correlation)
        );
    }

    private Long tokens(Record row, int index) {
        BigDecimal value = row.get(index, BigDecimal.class);

        return value == null ? null : value.longValue();
    }

    private Field<List<ProblemFile>> baseFilesField() {
        return multiset(
                select(FILE_PATH, FILE_CONTENT)
                        .from(ATTEMPT_FILE)
                        .where(FILE_ATTEMPT_ID.eq(ID))
                        .orderBy(FILE_ORDINAL)
        ).convertFrom(result -> result.map(record -> new ProblemFile(record.value1(), record.value2())));
    }
}
