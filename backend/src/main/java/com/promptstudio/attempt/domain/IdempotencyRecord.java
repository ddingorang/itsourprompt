package com.promptstudio.attempt.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 처리 중이거나(PENDING) 이미 끝난(COMPLETED) 요청의 선점 기록.
 */
public record IdempotencyRecord(
        String key,
        Long userId,
        UUID guestSessionId,
        Long attemptId,
        Status status,
        Instant createdAt
) {

    public enum Status {
        PENDING, COMPLETED
    }
}
