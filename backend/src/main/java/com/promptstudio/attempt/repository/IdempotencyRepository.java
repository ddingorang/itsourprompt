package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.IdempotencyRecord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository {

    boolean tryInsertPending(String key, Long userId, UUID guestSessionId, Instant now);

    Optional<IdempotencyRecord> findByKey(String key);

    /**
     * staleBefore보다 오래된 PENDING 행만 현재 시각으로 갱신해 인수한다. 인수에 성공하면 true.
     */
    boolean tryTakeOver(String key, Instant staleBefore, Instant now);

    /**
     * 호출자의 트랜잭션에 참여한다 — 비즈니스 결과와 같은 커밋으로 묶기 위해 자체 트랜잭션을 열지 않는다.
     */
    void markCompleted(String key, Long attemptId);

    void delete(String key);

    void deleteByGuestSessionId(UUID guestSessionId);
}
