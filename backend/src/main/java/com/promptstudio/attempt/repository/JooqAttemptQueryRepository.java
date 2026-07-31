package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.problem.domain.ProblemFile;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.promptstudio.attempt.repository.AttemptTables.ATTEMPT;
import static com.promptstudio.attempt.repository.AttemptTables.ATTEMPT_FILE;
import static com.promptstudio.attempt.repository.AttemptTables.ATTEMPT_TURN;
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
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;

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

    private Optional<AttemptView> findByCondition(org.jooq.Condition condition) {
        return dsl.select(ID, PROBLEM_ID, baseFilesField(), turnsField(), STATUS, FEEDBACK)
                .from(ATTEMPT)
                .where(condition)
                .fetchOptional(record -> AttemptView.reconstruct(
                        record.value1(),
                        record.value2(),
                        record.value3(),
                        record.value4(),
                        AttemptStatus.valueOf(record.value5()),
                        record.value6()
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
                select(TURN_USER_PROMPT, TURN_AI_SUMMARY, changes, toolCalls, TURN_FEEDBACK)
                        .from(ATTEMPT_TURN)
                        .where(TURN_ATTEMPT_ID.eq(ID))
                        .orderBy(TURN_ORDINAL)
        ).convertFrom(result -> result.map(record -> new AttemptView.TurnView(
                record.value1(), record.value2(), record.value3(), record.value4(), record.value5())));
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
