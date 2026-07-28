package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
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
import static com.promptstudio.attempt.repository.AttemptTables.CHANGE_ORDINAL;
import static com.promptstudio.attempt.repository.AttemptTables.CHANGE_PATH;
import static com.promptstudio.attempt.repository.AttemptTables.CHANGE_TURN_ID;
import static com.promptstudio.attempt.repository.AttemptTables.CHANGE_TYPE;
import static com.promptstudio.attempt.repository.AttemptTables.FILE_ATTEMPT_ID;
import static com.promptstudio.attempt.repository.AttemptTables.FILE_CONTENT;
import static com.promptstudio.attempt.repository.AttemptTables.FILE_ORDINAL;
import static com.promptstudio.attempt.repository.AttemptTables.FILE_PATH;
import static com.promptstudio.attempt.repository.AttemptTables.ID;
import static com.promptstudio.attempt.repository.AttemptTables.PROBLEM_ID;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_AI_SUMMARY;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_ATTEMPT_ID;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_FILE_CHANGE;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_ID;
import static com.promptstudio.attempt.repository.AttemptTables.TURN_ORDINAL;
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
        return dsl.select(ID, PROBLEM_ID, filesField(), turnsField())
                .from(ATTEMPT)
                .where(ID.eq(id))
                .fetchOptional(record -> new AttemptView(
                        record.value1(),
                        record.value2(),
                        record.value3(),
                        record.value4()
                ));
    }

    private Field<List<AttemptView.TurnView>> turnsField() {
        Field<List<FileChange>> changes = multiset(
                select(CHANGE_PATH, CHANGE_TYPE)
                        .from(TURN_FILE_CHANGE)
                        .where(CHANGE_TURN_ID.eq(TURN_ID))
                        .orderBy(CHANGE_ORDINAL)
        ).convertFrom(result -> result.map(record ->
                new FileChange(record.value1(), FileChange.ChangeType.valueOf(record.value2()))));

        return multiset(
                select(TURN_USER_PROMPT, TURN_AI_SUMMARY, changes)
                        .from(ATTEMPT_TURN)
                        .where(TURN_ATTEMPT_ID.eq(ID))
                        .orderBy(TURN_ORDINAL)
        ).convertFrom(result -> result.map(record ->
                new AttemptView.TurnView(record.value1(), record.value2(), record.value3())));
    }

    private Field<List<ProblemFile>> filesField() {
        return multiset(
                select(FILE_PATH, FILE_CONTENT)
                        .from(ATTEMPT_FILE)
                        .where(FILE_ATTEMPT_ID.eq(ID))
                        .orderBy(FILE_ORDINAL)
        ).convertFrom(result -> result.map(record -> new ProblemFile(record.value1(), record.value2())));
    }
}
