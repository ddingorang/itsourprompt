package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.Attempt;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MemoryAttemptRepository implements AttemptRepository {

    private final Map<Long, Attempt> attempts = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Attempt save(Attempt attempt) {
        Attempt saved = attempt.id() == null
                ? new Attempt(sequence.incrementAndGet(), attempt.problemId(), attempt.currentFiles(), attempt.turns())
                : attempt;
        attempts.put(saved.id(), saved);

        return saved;
    }

    @Override
    public Optional<Attempt> findById(Long id) {
        return Optional.ofNullable(attempts.get(id));
    }
}
