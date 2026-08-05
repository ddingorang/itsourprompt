package com.promptstudio.guest.repository;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.notExists;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.table;

/** 만료됐고 풀이 기록도 없는 게스트 세션만 지운다. 만료된 풀이 기록은 보존 정책을 정할 때 별도로 삭제한다. */
@Repository
public class GuestSessionCleanupRepository {

    private static final Table<?> GUEST_SESSION = table(name("guest_session"));
    private static final Field<UUID> GUEST_ID = field(name("guest_session", "id"), SQLDataType.UUID);
    private static final Field<Instant> EXPIRES_AT = field(name("guest_session", "expires_at"), SQLDataType.INSTANT);
    private static final Table<?> ATTEMPT = table(name("attempt"));
    private static final Field<UUID> ATTEMPT_GUEST_SESSION_ID =
            field(name("attempt", "guest_session_id"), SQLDataType.UUID);

    private final DSLContext dsl;

    public GuestSessionCleanupRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public int deleteExpiredOrphanSessions(Instant now) {
        return dsl.deleteFrom(GUEST_SESSION)
                .where(EXPIRES_AT.lt(now))
                .and(notExists(selectOne().from(ATTEMPT).where(ATTEMPT_GUEST_SESSION_ID.eq(GUEST_ID))))
                .execute();
    }
}
