package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.AttemptView;

import java.util.Optional;
import java.util.UUID;

public interface AttemptQueryRepository {

    Optional<AttemptView> findById(Long id);

    Optional<AttemptView> findByIdAndUserId(Long id, Long userId);

    Optional<AttemptView> findByIdAndGuestSessionId(Long id, UUID guestSessionId);
}
