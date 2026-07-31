package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.AttemptView;

import java.util.Optional;

public interface AttemptQueryRepository {

    Optional<AttemptView> findById(Long id);

    Optional<AttemptView> findByIdAndUserId(Long id, Long userId);
}
