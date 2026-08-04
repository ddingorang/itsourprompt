package com.promptstudio.attempt.repository;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

import java.time.Instant;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

final class CodeRunTables {

    static final Table<?> CODE_RUN = table(name("code_run"));
    static final Field<UUID> ID = field(name("code_run", "id"), SQLDataType.UUID);
    static final Field<Long> ATTEMPT_ID = field(name("code_run", "attempt_id"), SQLDataType.BIGINT);
    static final Field<Integer> TURN_ORDINAL = field(name("code_run", "turn_ordinal"), SQLDataType.INTEGER);
    static final Field<String> STATUS = field(name("code_run", "status"), SQLDataType.VARCHAR);
    static final Field<Integer> EXIT_CODE = field(name("code_run", "exit_code"), SQLDataType.INTEGER);
    static final Field<String> STDOUT = field(name("code_run", "stdout"), SQLDataType.CLOB);
    static final Field<String> STDERR = field(name("code_run", "stderr"), SQLDataType.CLOB);
    static final Field<Long> DURATION_MS = field(name("code_run", "duration_ms"), SQLDataType.BIGINT);
    static final Field<Instant> CREATED_AT = field(name("code_run", "created_at"), SQLDataType.INSTANT);
    static final Field<Instant> FINISHED_AT = field(name("code_run", "finished_at"), SQLDataType.INSTANT);

    static final Table<?> CODE_RUN_CASE = table(name("code_run_case"));
    static final Field<UUID> CASE_RUN_ID = field(name("code_run_case", "run_id"), SQLDataType.UUID);
    static final Field<Integer> CASE_ORDINAL = field(name("code_run_case", "ordinal"), SQLDataType.INTEGER);
    static final Field<String> CASE_CLASS_NAME = field(name("code_run_case", "class_name"), SQLDataType.VARCHAR);
    static final Field<String> CASE_NAME = field(name("code_run_case", "name"), SQLDataType.VARCHAR);
    static final Field<String> CASE_STATUS = field(name("code_run_case", "status"), SQLDataType.VARCHAR);
    static final Field<String> CASE_MESSAGE = field(name("code_run_case", "message"), SQLDataType.CLOB);
    static final Field<Long> CASE_DURATION_MS = field(name("code_run_case", "duration_ms"), SQLDataType.BIGINT);

    private CodeRunTables() {
    }
}
