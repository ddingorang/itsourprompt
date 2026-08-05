package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.AttemptLlmCall;
import org.springframework.data.jpa.repository.JpaRepository;

interface LlmCallJpaRepository extends JpaRepository<AttemptLlmCall, Long>, LlmCallRepository {
}
