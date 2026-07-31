package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.domain.CodeRunView;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.promptstudio.attempt.repository.CodeRunTables.ATTEMPT_ID;
import static com.promptstudio.attempt.repository.CodeRunTables.CODE_RUN;
import static com.promptstudio.attempt.repository.CodeRunTables.CREATED_AT;
import static com.promptstudio.attempt.repository.CodeRunTables.DURATION_MS;
import static com.promptstudio.attempt.repository.CodeRunTables.EXIT_CODE;
import static com.promptstudio.attempt.repository.CodeRunTables.FINISHED_AT;
import static com.promptstudio.attempt.repository.CodeRunTables.ID;
import static com.promptstudio.attempt.repository.CodeRunTables.STATUS;
import static com.promptstudio.attempt.repository.CodeRunTables.STDERR;
import static com.promptstudio.attempt.repository.CodeRunTables.STDOUT;

@Repository
public class JooqCodeRunRepository implements CodeRunRepository {

    private final DSLContext dsl;

    public JooqCodeRunRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean tryInsertQueued(UUID runId, Long attemptId, Instant now) {
        return dsl.insertInto(CODE_RUN)
                .columns(ID, ATTEMPT_ID, STATUS, CREATED_AT)
                .values(runId, attemptId, CodeRunStatus.QUEUED.name(), now)
                .onConflictDoNothing()
                .execute() == 1;
    }

    @Override
    public Optional<CodeRunView> findByIdAndAttemptId(UUID runId, Long attemptId) {
        return dsl.select(ID, ATTEMPT_ID, STATUS, EXIT_CODE, STDOUT, STDERR, DURATION_MS)
                .from(CODE_RUN)
                .where(ID.eq(runId))
                .and(ATTEMPT_ID.eq(attemptId))
                .fetchOptional(record -> new CodeRunView(
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
    public int expireStale(Instant staleBefore, Instant now) {
        return dsl.update(CODE_RUN)
                .set(STATUS, CodeRunStatus.RUNNER_ERROR.name())
                .set(STDERR, "워커가 제한 시간 안에 결과를 돌려주지 않았습니다.")
                .set(FINISHED_AT, now)
                .where(STATUS.eq(CodeRunStatus.QUEUED.name()))
                .and(CREATED_AT.lt(staleBefore))
                .execute();
    }

    @Override
    public int applyResult(CodeRunResult result, Instant finishedAt) {
        return dsl.update(CODE_RUN)
                .set(STATUS, result.status().name())
                .set(EXIT_CODE, result.exitCode())
                .set(STDOUT, result.stdout())
                .set(STDERR, result.stderr())
                .set(DURATION_MS, result.durationMs())
                .set(FINISHED_AT, finishedAt)
                .where(ID.eq(result.runId()))
                .and(STATUS.eq(CodeRunStatus.QUEUED.name()))
                .execute();
    }
}
