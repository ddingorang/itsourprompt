package com.promptstudio.problem.repository;

import com.promptstudio.problem.domain.SyncState;

import java.util.Optional;

public interface SyncStateRepository {

    SyncState save(SyncState syncState);

    Optional<SyncState> findById(Long id);
}
