package com.promptstudio.relay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 릴레이 턴 하나의 기록. "몇 번째 턴을 누가 썼는가"만 담는 사이드카다 —
 * 프롬프트·생성 코드·파일 변경은 방의 어템프트에 있고 {@code attemptTurnOrdinal}로 이어붙는다.
 *
 * <p>{@code turnIndex}와 {@code attemptTurnOrdinal}은 다른 값이다. 스킵된 턴은 릴레이 번호는
 * 차지하지만 어템프트에는 행이 생기지 않으므로, 스킵이 하나라도 생기면 둘이 어긋난다.
 */
@Entity
@Table(name = "relay_turn")
public class RelayTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, updatable = false)
    private Long roomId;

    @Column(name = "turn_index", nullable = false, updatable = false)
    private int turnIndex;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private Long authorUserId;

    @Column(name = "attempt_turn_ordinal", updatable = false)
    private Integer attemptTurnOrdinal;

    /** 이 턴 직후의 채점 run. 결과 원본은 code_run에 있고 여기엔 참조만 둔다. */
    @Column(name = "run_id")
    private UUID runId;

    /**
     * 채점 집계 스냅샷. code_run_case를 세면 나오는 값이지만, 점수가 "직전 턴 대비 증가분"이라
     * 턴마다 확정값이 남아야 계산이 안정적이다. NULL은 "0개 통과"가 아니라 "채점 없음"이다 —
     * 채점 실패·타임아웃으로 결과를 얻지 못한 턴이 여기 해당한다.
     */
    @Column(name = "passed_count")
    private Integer passedCount;

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected RelayTurn() {
    }

    private RelayTurn(
            Long roomId,
            int turnIndex,
            Long authorUserId,
            Integer attemptTurnOrdinal,
            Instant startedAt,
            Instant finishedAt
    ) {
        this.roomId = roomId;
        this.turnIndex = turnIndex;
        this.authorUserId = authorUserId;
        this.attemptTurnOrdinal = attemptTurnOrdinal;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    /**
     * 코드 생성까지 끝난 턴. 실패한 시도는 행을 만들지 않는다 — 같은 번호로 재시도하고,
     * 성공했을 때의 기록만 남는다.
     */
    public static RelayTurn completed(
            Long roomId, int turnIndex, Long authorUserId, int attemptTurnOrdinal, Instant startedAt) {
        return new RelayTurn(roomId, turnIndex, authorUserId, attemptTurnOrdinal, startedAt, Instant.now());
    }

    /**
     * 치지 않고 건너뛴 턴(이탈·입력 마감 초과). 어템프트에는 대응하는 턴이 없으므로
     * ordinal이 null이다 — 릴레이 번호는 차지하되 코드 진화에는 흔적이 없다.
     */
    public static RelayTurn skipped(Long roomId, int turnIndex, Long authorUserId) {
        Instant now = Instant.now();

        return new RelayTurn(roomId, turnIndex, authorUserId, null, now, now);
    }

    public boolean isSkipped() {
        return attemptTurnOrdinal == null;
    }

    public void attachRun(UUID runId) {
        this.runId = runId;
    }

    /**
     * @param passedCount 채점 결과를 얻지 못했으면(워커 죽음·타임아웃) null로 남긴다.
     *                    점수 계산은 null인 턴을 건너뛰고 가장 가까운 이전 값과 비교한다
     */
    public void recordGrading(Integer passedCount, Integer totalCount) {
        this.passedCount = passedCount;
        this.totalCount = totalCount;
    }

    public Long id() {
        return id;
    }

    public Long roomId() {
        return roomId;
    }

    public int turnIndex() {
        return turnIndex;
    }

    public Long authorUserId() {
        return authorUserId;
    }

    public Integer attemptTurnOrdinal() {
        return attemptTurnOrdinal;
    }

    public UUID runId() {
        return runId;
    }

    public Integer passedCount() {
        return passedCount;
    }

    public Integer totalCount() {
        return totalCount;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RelayTurn turn)) {
            return false;
        }

        return id != null && id.equals(turn.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
