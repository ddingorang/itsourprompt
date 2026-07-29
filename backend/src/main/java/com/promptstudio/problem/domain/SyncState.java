package com.promptstudio.problem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 문제 저장소를 마지막으로 동기화한 지점. 저장소가 하나뿐이라 행도 하나({@link #ID})만 쓴다.
 */
@Entity
@Table(name = "sync_state")
public class SyncState {

    public static final Long ID = 1L;

    @Id
    private Long id;

    @Column(name = "last_commit_sha", nullable = false)
    private String lastCommitSha;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected SyncState() {
    }

    public SyncState(String lastCommitSha, Instant syncedAt) {
        this.id = ID;
        this.lastCommitSha = lastCommitSha;
        this.syncedAt = syncedAt;
    }

    public void record(String lastCommitSha, Instant syncedAt) {
        this.lastCommitSha = lastCommitSha;
        this.syncedAt = syncedAt;
    }

    public Long id() {
        return id;
    }

    public String lastCommitSha() {
        return lastCommitSha;
    }

    public Instant syncedAt() {
        return syncedAt;
    }
}
