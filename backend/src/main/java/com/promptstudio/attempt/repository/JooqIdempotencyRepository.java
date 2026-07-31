package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.IdempotencyRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

import static com.promptstudio.attempt.repository.IdempotencyTables.IDEMPOTENCY_KEY;
import static com.promptstudio.attempt.repository.IdempotencyTables.IDEMPOTENCY_RECORD;
import static com.promptstudio.attempt.repository.IdempotencyTables.RECORD_ATTEMPT_ID;
import static com.promptstudio.attempt.repository.IdempotencyTables.RECORD_CREATED_AT;
import static com.promptstudio.attempt.repository.IdempotencyTables.RECORD_STATUS;
import static com.promptstudio.attempt.repository.IdempotencyTables.RECORD_USER_ID;

@Repository
public class JooqIdempotencyRepository implements IdempotencyRepository {

    private static final String PENDING = "PENDING";
    private static final String COMPLETED = "COMPLETED";

    private final DSLContext dsl;

    public JooqIdempotencyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean tryInsertPending(String key, Long userId, Instant now) {
        return dsl.insertInto(IDEMPOTENCY_RECORD)
                .columns(IDEMPOTENCY_KEY, RECORD_USER_ID, RECORD_STATUS, RECORD_CREATED_AT)
                .values(key, userId, PENDING, now)
                .onConflictDoNothing()
                .execute() == 1;
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String key) {
        return dsl.select(IDEMPOTENCY_KEY, RECORD_USER_ID, RECORD_ATTEMPT_ID, RECORD_STATUS, RECORD_CREATED_AT)
                .from(IDEMPOTENCY_RECORD)
                .where(IDEMPOTENCY_KEY.eq(key))
                .fetchOptional(record -> new IdempotencyRecord(
                        record.value1(),
                        record.value2(),
                        record.value3(),
                        IdempotencyRecord.Status.valueOf(record.value4()),
                        record.value5()
                ));
    }

    @Override
    public boolean tryTakeOver(String key, Instant staleBefore, Instant now) {
        return dsl.update(IDEMPOTENCY_RECORD)
                .set(RECORD_CREATED_AT, now)
                .where(IDEMPOTENCY_KEY.eq(key))
                .and(RECORD_STATUS.eq(PENDING))
                .and(RECORD_CREATED_AT.lt(staleBefore))
                .execute() == 1;
    }

    @Override
    public void markCompleted(String key, Long attemptId) {
        dsl.update(IDEMPOTENCY_RECORD)
                .set(RECORD_STATUS, COMPLETED)
                .set(RECORD_ATTEMPT_ID, attemptId)
                .where(IDEMPOTENCY_KEY.eq(key))
                .execute();
    }

    @Override
    public void delete(String key) {
        dsl.deleteFrom(IDEMPOTENCY_RECORD)
                .where(IDEMPOTENCY_KEY.eq(key))
                .execute();
    }
}
