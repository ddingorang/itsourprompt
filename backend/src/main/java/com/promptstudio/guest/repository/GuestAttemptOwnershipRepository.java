package com.promptstudio.guest.repository;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@Repository
public class GuestAttemptOwnershipRepository {

    private static final Table<?> ATTEMPT = table(name("attempt"));
    private static final Field<Long> USER_ID = field(name("attempt", "user_id"), SQLDataType.BIGINT);
    private static final Field<UUID> GUEST_SESSION_ID = field(name("attempt", "guest_session_id"), SQLDataType.UUID);

    private final DSLContext dsl;

    public GuestAttemptOwnershipRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public int transferToUser(UUID guestSessionId, Long userId) {
        return dsl.update(ATTEMPT)
                .set(USER_ID, userId)
                .set(GUEST_SESSION_ID, (UUID) null)
                .where(GUEST_SESSION_ID.eq(guestSessionId))
                .execute();
    }
}
