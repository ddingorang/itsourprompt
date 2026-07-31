package com.promptstudio.me.repository;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.me.domain.SubmittedAttempt;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqMyAttemptQueryRepository implements MyAttemptQueryRepository {

    private static final Table<?> ATTEMPT = table(name("attempt"));
    private static final Field<Long> ATTEMPT_ID = field(name("attempt", "id"), SQLDataType.BIGINT);
    private static final Field<Long> ATTEMPT_PROBLEM_ID = field(name("attempt", "problem_id"), SQLDataType.BIGINT);
    private static final Field<Long> ATTEMPT_USER_ID = field(name("attempt", "user_id"), SQLDataType.BIGINT);
    private static final Field<String> ATTEMPT_STATUS = field(name("attempt", "status"), SQLDataType.VARCHAR);
    private static final Field<Instant> ATTEMPT_SUBMITTED_AT =
            field(name("attempt", "submitted_at"), SQLDataType.INSTANT);

    private static final Table<?> PROBLEM = table(name("problem"));
    private static final Field<Long> PROBLEM_ID = field(name("problem", "id"), SQLDataType.BIGINT);
    private static final Field<String> PROBLEM_TITLE = field(name("problem", "title"), SQLDataType.VARCHAR);

    private final DSLContext dsl;

    public JooqMyAttemptQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubmittedAttempt> findSubmittedByUserId(Long userId) {
        return dsl.select(ATTEMPT_ID, ATTEMPT_PROBLEM_ID, PROBLEM_TITLE, ATTEMPT_SUBMITTED_AT)
                .from(ATTEMPT)
                .join(PROBLEM).on(ATTEMPT_PROBLEM_ID.eq(PROBLEM_ID))
                .where(ATTEMPT_USER_ID.eq(userId))
                .and(ATTEMPT_STATUS.eq(AttemptStatus.SUBMITTED.name()))
                .orderBy(ATTEMPT_SUBMITTED_AT.desc().nullsLast(), ATTEMPT_ID.desc())
                .fetch(record -> new SubmittedAttempt(
                        record.value1(), record.value2(), record.value3(), record.value4()));
    }
}
