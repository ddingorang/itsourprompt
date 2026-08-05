package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.Attempt;

import java.util.Optional;
import java.util.UUID;

public interface AttemptRepository {

    Attempt save(Attempt attempt);

    Optional<Attempt> findById(Long id);

    Optional<Attempt> findByIdAndUserId(Long id, Long userId);

    Optional<Attempt> findByIdAndGuestSessionId(Long id, UUID guestSessionId);
}
