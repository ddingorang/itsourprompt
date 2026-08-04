package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.CodeRunCase;
import com.promptstudio.attempt.domain.CodeRunCaseStatus;
import com.promptstudio.attempt.domain.CodeRunCaseTally;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.domain.CodeRunSummary;
import com.promptstudio.attempt.domain.CodeRunView;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.count;

import static com.promptstudio.attempt.repository.CodeRunTables.ATTEMPT_ID;
import static com.promptstudio.attempt.repository.CodeRunTables.CASE_CLASS_NAME;
import static com.promptstudio.attempt.repository.CodeRunTables.CASE_DURATION_MS;
import static com.promptstudio.attempt.repository.CodeRunTables.CASE_MESSAGE;
import static com.promptstudio.attempt.repository.CodeRunTables.CASE_NAME;
import static com.promptstudio.attempt.repository.CodeRunTables.CASE_ORDINAL;
import static com.promptstudio.attempt.repository.CodeRunTables.CASE_RUN_ID;
import static com.promptstudio.attempt.repository.CodeRunTables.CASE_STATUS;
import static com.promptstudio.attempt.repository.CodeRunTables.CODE_RUN;
import static com.promptstudio.attempt.repository.CodeRunTables.CODE_RUN_CASE;
import static com.promptstudio.attempt.repository.CodeRunTables.CREATED_AT;
import static com.promptstudio.attempt.repository.CodeRunTables.DURATION_MS;
import static com.promptstudio.attempt.repository.CodeRunTables.EXIT_CODE;
import static com.promptstudio.attempt.repository.CodeRunTables.FINISHED_AT;
import static com.promptstudio.attempt.repository.CodeRunTables.ID;
import static com.promptstudio.attempt.repository.CodeRunTables.STATUS;
import static com.promptstudio.attempt.repository.CodeRunTables.STDERR;
import static com.promptstudio.attempt.repository.CodeRunTables.STDOUT;
import static com.promptstudio.attempt.repository.CodeRunTables.TURN_ORDINAL;

@Repository
public class JooqCodeRunRepository implements CodeRunRepository {

    private final DSLContext dsl;

    public JooqCodeRunRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean tryInsertQueued(UUID runId, Long attemptId, Integer turnOrdinal, Instant now) {
        return dsl.insertInto(CODE_RUN)
                .columns(ID, ATTEMPT_ID, TURN_ORDINAL, STATUS, CREATED_AT)
                .values(runId, attemptId, turnOrdinal, CodeRunStatus.QUEUED.name(), now)
                .onConflictDoNothing()
                .execute() == 1;
    }

    @Override
    public Optional<CodeRunView> findByIdAndAttemptId(UUID runId, Long attemptId) {
        return dsl.select(ID, ATTEMPT_ID, TURN_ORDINAL, STATUS, EXIT_CODE, STDOUT, STDERR, DURATION_MS)
                .from(CODE_RUN)
                .where(ID.eq(runId))
                .and(ATTEMPT_ID.eq(attemptId))
                .fetchOptional(record -> new CodeRunView(
                        record.value1(),
                        record.value2(),
                        record.value3(),
                        CodeRunStatus.valueOf(record.value4()),
                        record.value5(),
                        record.value6(),
                        record.value7(),
                        record.value8()
                ));
    }

    /**
     * created_at 뒤에 id를 덧붙여 정렬한다. 같은 시각의 두 행이 호출마다 다른 순서로 나오면
     * 목록을 그리는 화면이 흔들린다 — UUID 순서에 의미는 없고 안정성만 취한다.
     */
    @Override
    public List<CodeRunSummary> findAllByAttemptId(Long attemptId) {
        return dsl.select(ID, TURN_ORDINAL, STATUS, EXIT_CODE, DURATION_MS, CREATED_AT, FINISHED_AT)
                .from(CODE_RUN)
                .where(ATTEMPT_ID.eq(attemptId))
                .orderBy(CREATED_AT.desc(), ID.desc())
                .fetch(record -> new CodeRunSummary(
                        record.value1(),
                        record.value2(),
                        CodeRunStatus.valueOf(record.value3()),
                        record.value4(),
                        record.value5(),
                        record.value6(),
                        record.value7()
                ));
    }

    @Override
    public List<CodeRunCase> findCasesByRunId(UUID runId) {
        return dsl.select(CASE_CLASS_NAME, CASE_NAME, CASE_STATUS, CASE_MESSAGE, CASE_DURATION_MS)
                .from(CODE_RUN_CASE)
                .where(CASE_RUN_ID.eq(runId))
                .orderBy(CASE_ORDINAL.asc())
                .fetch(record -> new CodeRunCase(
                        record.value1(),
                        record.value2(),
                        CodeRunCaseStatus.valueOf(record.value3()),
                        record.value4(),
                        record.value5()
                ));
    }

    /**
     * 실행마다 쿼리를 날리지 않도록 한 번에 GROUP BY 한다. 목록에 실행이 여러 건 있어도 쿼리는 하나다.
     */
    @Override
    public Map<UUID, CodeRunCaseTally> tallyCasesByRunIds(Collection<UUID> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }

        return dsl.select(
                        CASE_RUN_ID,
                        count(),
                        count().filterWhere(CASE_STATUS.eq(CodeRunCaseStatus.PASSED.name())),
                        count().filterWhere(CASE_STATUS.eq(CodeRunCaseStatus.FAILED.name())),
                        count().filterWhere(CASE_STATUS.eq(CodeRunCaseStatus.ERROR.name())),
                        count().filterWhere(CASE_STATUS.eq(CodeRunCaseStatus.SKIPPED.name())))
                .from(CODE_RUN_CASE)
                .where(CASE_RUN_ID.in(runIds))
                .groupBy(CASE_RUN_ID)
                .fetchMap(
                        record -> record.value1(),
                        record -> new CodeRunCaseTally(
                                record.value2(), record.value3(), record.value4(),
                                record.value5(), record.value6())
                );
    }

    @Override
    public int expireStale(Instant staleBefore, Instant now) {
        return dsl.update(CODE_RUN)
                .set(STATUS, CodeRunStatus.RUNNER_ERROR.name())
                .set(STDERR, "워커가 제한 시간 안에 결과를 돌려주지 않았습니다.")
                .set(FINISHED_AT, now)
                .where(STATUS.eq(CodeRunStatus.QUEUED.name()))
                .and(CREATED_AT.lt(staleBefore))
                .execute();
    }

    /**
     * 케이스 삽입을 같은 트랜잭션에 묶는다. UPDATE가 0행이면(이미 종료된 run) 케이스도 넣지 않아야
     * 하는데, 나눠 두면 그 사이에 결과가 두 번 도착했을 때 케이스만 중복으로 쌓인다.
     */
    @Override
    @Transactional
    public int applyResult(CodeRunResult result, Instant finishedAt) {
        int applied = dsl.update(CODE_RUN)
                .set(STATUS, result.status().name())
                .set(EXIT_CODE, result.exitCode())
                .set(STDOUT, result.stdout())
                .set(STDERR, result.stderr())
                .set(DURATION_MS, result.durationMs())
                .set(FINISHED_AT, finishedAt)
                .where(ID.eq(result.runId()))
                .and(STATUS.eq(CodeRunStatus.QUEUED.name()))
                .execute();

        if (applied == 1) {
            insertCases(result);
        }

        return applied;
    }

    private void insertCases(CodeRunResult result) {
        List<CodeRunCase> cases = result.cases();

        if (cases == null || cases.isEmpty()) {
            return;
        }

        var insert = dsl.insertInto(CODE_RUN_CASE)
                .columns(CASE_RUN_ID, CASE_ORDINAL, CASE_CLASS_NAME, CASE_NAME,
                        CASE_STATUS, CASE_MESSAGE, CASE_DURATION_MS);

        for (int ordinal = 0; ordinal < cases.size(); ordinal++) {
            CodeRunCase testCase = cases.get(ordinal);

            insert = insert.values(
                    result.runId(),
                    ordinal,
                    testCase.className(),
                    testCase.name(),
                    testCase.status().name(),
                    testCase.message(),
                    testCase.durationMs()
            );
        }

        insert.execute();
    }
}
