package com.promptstudio.problem.repository;

import com.promptstudio.problem.domain.SyncState;
import org.springframework.data.jpa.repository.JpaRepository;

interface SyncStateJpaRepository extends JpaRepository<SyncState, Long>, SyncStateRepository {
}
