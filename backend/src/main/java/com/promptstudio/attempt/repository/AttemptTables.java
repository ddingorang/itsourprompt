package com.promptstudio.attempt.repository;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

import java.util.UUID;
import java.math.BigDecimal;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

final class AttemptTables {

    static final Table<?> ATTEMPT = table(name("attempt"));
    static final Field<Long> ID = field(name("attempt", "id"), SQLDataType.BIGINT);
    static final Field<Long> PROBLEM_ID = field(name("attempt", "problem_id"), SQLDataType.BIGINT);
    static final Field<Long> USER_ID = field(name("attempt", "user_id"), SQLDataType.BIGINT);
    static final Field<UUID> GUEST_SESSION_ID = field(name("attempt", "guest_session_id"), SQLDataType.UUID);
    static final Field<String> STATUS = field(name("attempt", "status"), SQLDataType.VARCHAR);
    static final Field<String> FEEDBACK = field(name("attempt", "feedback"), SQLDataType.CLOB);
    static final Field<String> PATTERN_FEEDBACK = field(name("attempt", "pattern_feedback"), SQLDataType.CLOB);

    static final Table<?> USERS = table(name("users"));
    static final Field<Long> USERS_ID = field(name("users", "id"), SQLDataType.BIGINT);
    static final Field<String> USERS_NICKNAME = field(name("users", "nickname"), SQLDataType.VARCHAR);

    static final Table<?> ATTEMPT_FILE = table(name("attempt_file"));
    static final Field<Long> FILE_ATTEMPT_ID = field(name("attempt_file", "attempt_id"), SQLDataType.BIGINT);
    static final Field<Integer> FILE_ORDINAL = field(name("attempt_file", "ordinal"), SQLDataType.INTEGER);
    static final Field<String> FILE_PATH = field(name("attempt_file", "path"), SQLDataType.VARCHAR);
    static final Field<String> FILE_CONTENT = field(name("attempt_file", "content"), SQLDataType.CLOB);

    static final Table<?> ATTEMPT_TURN = table(name("attempt_turn"));
    static final Field<Long> TURN_ID = field(name("attempt_turn", "id"), SQLDataType.BIGINT);
    static final Field<Long> TURN_ATTEMPT_ID = field(name("attempt_turn", "attempt_id"), SQLDataType.BIGINT);
    static final Field<Integer> TURN_ORDINAL = field(name("attempt_turn", "ordinal"), SQLDataType.INTEGER);
    static final Field<String> TURN_USER_PROMPT = field(name("attempt_turn", "user_prompt"), SQLDataType.CLOB);
    static final Field<String> TURN_AI_SUMMARY = field(name("attempt_turn", "ai_summary"), SQLDataType.CLOB);
    static final Field<String> TURN_FEEDBACK = field(name("attempt_turn", "feedback"), SQLDataType.CLOB);
    static final Field<String> TURN_PATTERN_FEEDBACK =
            field(name("attempt_turn", "pattern_feedback"), SQLDataType.CLOB);

    static final Table<?> TURN_FILE_CHANGE = table(name("turn_file_change"));
    static final Field<Long> CHANGE_TURN_ID = field(name("turn_file_change", "turn_id"), SQLDataType.BIGINT);
    static final Field<Integer> CHANGE_ORDINAL = field(name("turn_file_change", "ordinal"), SQLDataType.INTEGER);
    static final Field<String> CHANGE_PATH = field(name("turn_file_change", "path"), SQLDataType.VARCHAR);
    static final Field<String> CHANGE_TYPE = field(name("turn_file_change", "change_type"), SQLDataType.VARCHAR);
    static final Field<String> CHANGE_CONTENT = field(name("turn_file_change", "content"), SQLDataType.CLOB);

    static final Table<?> TURN_TOOL_CALL = table(name("turn_tool_call"));
    static final Field<Long> TOOL_CALL_TURN_ID = field(name("turn_tool_call", "turn_id"), SQLDataType.BIGINT);
    static final Field<Integer> TOOL_CALL_ORDINAL = field(name("turn_tool_call", "ordinal"), SQLDataType.INTEGER);
    static final Field<String> TOOL_CALL_TOOL = field(name("turn_tool_call", "tool"), SQLDataType.VARCHAR);
    static final Field<String> TOOL_CALL_PATH = field(name("turn_tool_call", "path"), SQLDataType.VARCHAR);

    static final Table<?> ATTEMPT_LLM_CALL = table(name("attempt_llm_call"));
    static final Field<Long> CALL_ATTEMPT_ID = field(name("attempt_llm_call", "attempt_id"), SQLDataType.BIGINT);
    static final Field<Integer> CALL_TURN_ORDINAL =
            field(name("attempt_llm_call", "turn_ordinal"), SQLDataType.INTEGER);
    static final Field<String> CALL_MODEL = field(name("attempt_llm_call", "model"), SQLDataType.VARCHAR);
    static final Field<Long> CALL_INPUT_TOKENS =
            field(name("attempt_llm_call", "input_tokens"), SQLDataType.BIGINT);
    static final Field<Long> CALL_OUTPUT_TOKENS =
            field(name("attempt_llm_call", "output_tokens"), SQLDataType.BIGINT);
    static final Field<Long> CALL_CACHED_INPUT_TOKENS =
            field(name("attempt_llm_call", "cached_input_tokens"), SQLDataType.BIGINT);
    static final Field<Long> CALL_REASONING_TOKENS =
            field(name("attempt_llm_call", "reasoning_tokens"), SQLDataType.BIGINT);
    static final Field<Long> CALL_LATENCY_MS =
            field(name("attempt_llm_call", "latency_ms"), SQLDataType.BIGINT);
    static final Field<BigDecimal> CALL_COST = field(name("attempt_llm_call", "cost"), SQLDataType.DECIMAL);

    private AttemptTables() {
    }
}
