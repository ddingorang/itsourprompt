package com.promptstudio.attempt.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 어템프트를 소유하는 주체. 로그인 사용자와 게스트 세션 중 정확히 하나만 가질 수 있다.
 */
public record AttemptOwner(Long userId, UUID guestSessionId) {

    public AttemptOwner {
        if ((userId == null) == (guestSessionId == null)) {
            throw new IllegalArgumentException("Attempt owner must be exactly one of user or guest session");
        }
    }

    public static AttemptOwner user(Long userId) {
        return new AttemptOwner(Objects.requireNonNull(userId, "userId must not be null"), null);
    }

    public static AttemptOwner guest(UUID guestSessionId) {
        return new AttemptOwner(null, Objects.requireNonNull(guestSessionId, "guestSessionId must not be null"));
    }

    public boolean isUser() {
        return userId != null;
    }

    public String idempotencyScope() {
        return isUser() ? "user:" + userId : "guest:" + guestSessionId;
    }
}
