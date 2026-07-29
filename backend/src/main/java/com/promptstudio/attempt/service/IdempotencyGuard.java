package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.IdempotencyRecord;
import com.promptstudio.attempt.exception.DuplicateRequestException;
import com.promptstudio.attempt.repository.IdempotencyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Idempotency-Key 선점을 담당한다. 선점은 AI 호출 전에 커밋되어야 다른 요청에 보이므로 별도 트랜잭션에서 처리한다.
 */
@Component
class IdempotencyGuard {

    /**
     * 이 시간을 넘긴 PENDING은 소유자가 죽은 것으로 본다 — AI 호출 상한 5분 + 저장 트랜잭션 여유.
     */
    private static final Duration PENDING_TAKEOVER_TTL = Duration.ofMinutes(7);

    private final IdempotencyRepository idempotencyRepository;

    IdempotencyGuard(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Reservation reserve(String key) {
        Instant now = Instant.now();

        if (idempotencyRepository.tryInsertPending(key, now)) {
            return new Reservation.Acquired();
        }

        Optional<IdempotencyRecord> existing = idempotencyRepository.findByKey(key);

        // 실패한 요청이 그 사이에 행을 지웠다면 한 번만 다시 선점해 본다.
        if (existing.isEmpty()) {
            return retryInsert(key, now);
        }

        IdempotencyRecord record = existing.get();
        if (record.status() == IdempotencyRecord.Status.COMPLETED) {
            return new Reservation.Replay(record.attemptId());
        }

        // 남은 경우는 PENDING이다. TTL을 넘겼다면 원 소유자는 이미 죽었으므로 인수해 다시 실행한다.
        if (idempotencyRepository.tryTakeOver(key, now.minus(PENDING_TAKEOVER_TTL), now)) {
            return new Reservation.Acquired();
        }

        throw new DuplicateRequestException(key);
    }

    private Reservation retryInsert(String key, Instant now) {
        if (idempotencyRepository.tryInsertPending(key, now)) {
            return new Reservation.Acquired();
        }

        throw new DuplicateRequestException(key);
    }

    /**
     * 선점을 되돌린다. 실패한 요청의 키로 곧바로 재시도할 수 있게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void release(String key) {
        idempotencyRepository.delete(key);
    }

    sealed interface Reservation {

        record Acquired() implements Reservation {
        }

        record Replay(Long attemptId) implements Reservation {
        }
    }
}
