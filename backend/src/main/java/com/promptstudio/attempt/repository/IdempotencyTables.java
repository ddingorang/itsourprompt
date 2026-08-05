package com.promptstudio.attempt.repository;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

import java.time.Instant;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

final class IdempotencyTables {

    static final Table<?> IDEMPOTENCY_RECORD = table(name("idempotency_record"));
    static final Field<String> IDEMPOTENCY_KEY =
            field(name("idempotency_record", "idempotency_key"), SQLDataType.VARCHAR);
    static final Field<Long> RECORD_ATTEMPT_ID =
            field(name("idempotency_record", "attempt_id"), SQLDataType.BIGINT);
    static final Field<Long> RECORD_USER_ID =
            field(name("idempotency_record", "user_id"), SQLDataType.BIGINT);
    static final Field<UUID> RECORD_GUEST_SESSION_ID =
            field(name("idempotency_record", "guest_session_id"), SQLDataType.UUID);
    static final Field<String> RECORD_STATUS =
            field(name("idempotency_record", "status"), SQLDataType.VARCHAR);
    static final Field<Instant> RECORD_CREATED_AT =
            field(name("idempotency_record", "created_at"), SQLDataType.INSTANT);

    private IdempotencyTables() {
    }
}
