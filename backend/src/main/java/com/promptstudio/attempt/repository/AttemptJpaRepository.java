package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;

interface AttemptJpaRepository extends JpaRepository<Attempt, Long>, AttemptRepository {
}
