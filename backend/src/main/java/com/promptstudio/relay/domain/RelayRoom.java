package com.promptstudio.relay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 릴레이 게임 방.
 *
 * <p>코드 진화는 이 엔티티가 하지 않는다. 방이 시작될 때 어템프트 하나를 만들고, 프롬프트·생성 코드·
 * 파일 변경·채점은 전부 그 어템프트에 쌓인다. 이 엔티티가 아는 것은 "누구 차례인가"와 "지금 어느
 * 단계인가"뿐이다.
 *
 * <p>인가도 두 층으로 나뉜다. "지금 네 순서인가"는 여기서 판정하고, 어템프트에 턴을 붙이는 것은
 * 방장 신원으로 기존 어템프트 서비스에 위임한다 — 어템프트 입장에서는 평범한 자기 소유 어템프트다.
 */
@Entity
@Table(name = "relay_room")
public class RelayRoom {

    /** 1인 릴레이는 릴레이가 아니다. */
    public static final int MIN_PARTICIPANTS = 2;

    /**
     * WebRTC를 mesh(각자 N-1 연결)로 붙이므로 이 인원을 넘기면 업링크가 포화된다.
     * 더 늘리려면 SFU가 필요하다.
     */
    public static final int MAX_PARTICIPANTS = 6;

    public static final int MIN_LAPS = 1;

    /**
     * 턴 하나가 코드 생성 + 채점으로 1분 가까이 걸린다. 인원 × 바퀴가 곧 게임 길이다.
     */
    public static final int MAX_LAPS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false, updatable = false)
    private Long problemId;

    /**
     * 게임을 시작할 권한. 방장이 시작 전에 나가면 남은 참가자에게 넘어가므로 불변이 아니다.
     */
    @Column(name = "host_user_id", nullable = false)
    private Long hostUserId;

    @Column(name = "attempt_id")
    private Long attemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RelayRoomStatus status = RelayRoomStatus.WAITING;

    @Column(name = "total_laps", nullable = false, updatable = false)
    private int totalLaps;

    @Column(name = "max_participants", nullable = false, updatable = false)
    private int maxParticipants;

    @Column(name = "seat_count")
    private Integer seatCount;

    @Column(name = "current_turn_index", nullable = false)
    private int currentTurnIndex;

    @Column(name = "turn_deadline")
    private Instant turnDeadline;

    @Column(name = "current_run_id")
    private UUID currentRunId;

    @Column(name = "baseline_run_id")
    private UUID baselineRunId;

    @Column(name = "baseline_passed")
    private Integer baselinePassed;

    @Column(name = "baseline_total")
    private Integer baselineTotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected RelayRoom() {
    }

    private RelayRoom(Long problemId, Long hostUserId, int totalLaps, int maxParticipants, Instant createdAt) {
        this.problemId = problemId;
        this.hostUserId = hostUserId;
        this.totalLaps = totalLaps;
        this.maxParticipants = maxParticipants;
        this.createdAt = createdAt;
    }

    /**
     * 방을 열어 입장을 받기 시작한다. 방장은 개설과 동시에 첫 번째 참가자가 되므로
     * 1번 좌석은 항상 방장이다(등록은 서비스가 한다).
     */
    public static RelayRoom open(Long problemId, Long hostUserId, int totalLaps, int maxParticipants) {
        if (totalLaps < MIN_LAPS || totalLaps > MAX_LAPS) {
            throw new IllegalArgumentException("totalLaps must be between " + MIN_LAPS + " and " + MAX_LAPS);
        }
        if (maxParticipants < MIN_PARTICIPANTS || maxParticipants > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "maxParticipants must be between " + MIN_PARTICIPANTS + " and " + MAX_PARTICIPANTS);
        }

        return new RelayRoom(problemId, hostUserId, totalLaps, maxParticipants, Instant.now());
    }

    public boolean isWaiting() {
        return status == RelayRoomStatus.WAITING;
    }

    public boolean isPlaying() {
        return status == RelayRoomStatus.PLAYING;
    }

    /**
     * 좌석 확정과 함께 게임을 시작한다. 좌석 수는 이 순간의 참가자 수로 굳고 이후 이탈해도 변하지
     * 않는다 — 진행 인덱스에서 좌석과 바퀴를 파생하는 나눗셈의 분모이기 때문이다.
     *
     * @param firstTurnDeadline 첫 주자의 입력 마감. 이 시각까지 프롬프트가 없으면 스킵된다
     */
    public void startGame(int seatCount, Long attemptId, Instant firstTurnDeadline) {
        if (!isWaiting()) {
            throw new IllegalStateException("Room " + id + " is not waiting: " + status);
        }

        this.seatCount = seatCount;
        this.attemptId = attemptId;
        this.status = RelayRoomStatus.PLAYING;
        this.turnDeadline = firstTurnDeadline;
        this.startedAt = Instant.now();
    }

    /**
     * 생성이 끝난 턴을 채점 대기로 넘긴다. 좌석은 아직 전진하지 않는다 — 다음 주자는 채점
     * 결과(통과 현황)를 보고 시작해야 하기 때문이다.
     *
     * @param gradingDeadline 이 시각까지 채점 결과가 오지 않으면 스케줄러가 채점 없이 전진시킨다.
     *                        워커가 죽으면 결과 이벤트가 영영 오지 않으므로 마감이 없으면 방이
     *                        TURN_GRADING에 갇힌다
     */
    public void finishGeneration(Instant gradingDeadline) {
        if (status != RelayRoomStatus.TURN_GENERATING) {
            throw new IllegalStateException("Room " + id + " has no turn in progress: " + status);
        }

        this.status = RelayRoomStatus.TURN_GRADING;
        this.turnDeadline = gradingDeadline;
    }

    /**
     * 생성 실패. 좌석을 전진시키지 않고 같은 주자가 재시도할 수 있게 되돌린다.
     *
     * @param retryDeadline 재시도 입력 마감. 새로 주지 않으면 생성에 쓴 시간만큼 깎인 옛 마감이
     *                      남아, 502를 받은 주자가 재시도할 틈도 없이 스킵될 수 있다
     */
    public void failTurn(Instant retryDeadline) {
        if (status == RelayRoomStatus.TURN_GENERATING) {
            this.status = RelayRoomStatus.PLAYING;
            this.turnDeadline = retryDeadline;
        }
    }

    /** 이번 턴의 채점 run. 결과 이벤트가 이 값으로 방을 되찾는다. */
    public void attachGradingRun(UUID runId) {
        if (status != RelayRoomStatus.TURN_GRADING) {
            throw new IllegalStateException("Room " + id + " is not grading: " + status);
        }

        this.currentRunId = runId;
    }

    /**
     * 채점이 끝나(혹은 포기하고) 좌석을 전진시킨다. 마지막 턴이었으면 피드백 생성으로 넘어간다 —
     * FINISHED는 피드백까지 준비된 뒤다.
     *
     * @param nextTurnDeadline 다음 주자의 입력 마감. 마지막 턴이었으면 쓰이지 않는다
     */
    public void advanceAfterGrading(Instant nextTurnDeadline) {
        if (status != RelayRoomStatus.TURN_GRADING) {
            throw new IllegalStateException("Room " + id + " is not grading: " + status);
        }

        this.currentTurnIndex++;
        this.currentRunId = null;

        advanceInto(nextTurnDeadline);
    }

    /**
     * 현재 좌석을 치지 않고 건너뛴다. 이탈한 주자의 차례이거나 입력 마감을 넘긴 경우다.
     * 생성·채점 없이 곧장 다음 좌석(또는 피드백 생성)으로 간다.
     */
    public void skipTurn(Instant nextTurnDeadline) {
        if (status != RelayRoomStatus.PLAYING) {
            throw new IllegalStateException("Room " + id + " is not playing: " + status);
        }

        this.currentTurnIndex++;

        advanceInto(nextTurnDeadline);
    }

    /** 입력 마감을 넘겼는가. PLAYING이 아닌 상태에서는 항상 거짓이다. */
    public boolean isTurnInputExpired(Instant now) {
        return status == RelayRoomStatus.PLAYING
                && turnDeadline != null
                && !turnDeadline.isAfter(now);
    }

    private void advanceInto(Instant nextTurnDeadline) {
        if (currentTurnIndex >= seatCount * totalLaps) {
            this.status = RelayRoomStatus.FEEDBACK_GENERATING;
            this.turnDeadline = null;
        } else {
            this.status = RelayRoomStatus.PLAYING;
            this.turnDeadline = nextTurnDeadline;
        }
    }

    /** 시작 스켈레톤의 채점 요청을 기록한다. 결과가 오기 전까지 기준선 집계는 비어 있다. */
    public void attachBaselineRun(UUID runId) {
        this.baselineRunId = runId;
        this.currentRunId = runId;
    }

    public boolean isBaselineRun(UUID runId) {
        return runId != null && runId.equals(baselineRunId);
    }

    public void recordBaseline(Integer passed, Integer total) {
        this.baselinePassed = passed;
        this.baselineTotal = total;

        // 베이스라인이 턴 채점과 겹쳐 있었을 수 있다(첫 턴이 아주 빨랐던 경우).
        // 그때 currentRunId는 이미 턴 run으로 바뀌어 있으므로 베이스라인일 때만 지운다.
        if (isBaselineRun(currentRunId)) {
            this.currentRunId = null;
        }
    }

    /** 피드백까지 준비 완료. 이때에야 게임이 닫힌다. */
    public void finishGame() {
        if (status != RelayRoomStatus.FEEDBACK_GENERATING) {
            throw new IllegalStateException("Room " + id + " is not generating feedback: " + status);
        }

        this.status = RelayRoomStatus.FINISHED;
        this.finishedAt = Instant.now();
    }

    /**
     * 지금 차례인 좌석(0-based). 게임이 릴레이 중이 아니면 null이다 — FINISHED에서 나머지 연산을
     * 하면 0번 좌석이 나와 "1번 주자의 차례"로 잘못 읽힌다.
     */
    public Integer currentSeat() {
        return inRelay() ? currentTurnIndex % seatCount : null;
    }

    /** 지금 몇 바퀴째인지(0-based). {@link #currentSeat()}와 같은 조건에서만 값이 있다. */
    public Integer currentLap() {
        return inRelay() ? currentTurnIndex / seatCount : null;
    }

    /** 총 턴 수 = 좌석 × 바퀴. 좌석이 확정되기 전에는 null이다. */
    public Integer totalTurns() {
        return seatCount == null ? null : seatCount * totalLaps;
    }

    private boolean inRelay() {
        return seatCount != null
                && status != RelayRoomStatus.WAITING
                && status != RelayRoomStatus.FINISHED
                // 모든 턴을 소진한 뒤(피드백 생성 중)에는 차례가 없다. 나머지 연산이 한 바퀴
                // 돌아 0번 좌석이 나오면 "1번 주자의 차례"로 잘못 읽힌다.
                && currentTurnIndex < seatCount * totalLaps;
    }

    /**
     * 방장이 시작 전에 나갈 때 남은 참가자에게 권한을 넘긴다. 넘기지 않으면 나간 사람의 ID가
     * 남아 아무도 게임을 시작할 수 없는 방이 된다.
     */
    public void delegateHostTo(Long userId) {
        this.hostUserId = userId;
    }

    public boolean isHost(Long userId) {
        return hostUserId.equals(userId);
    }

    public Long id() {
        return id;
    }

    public Long problemId() {
        return problemId;
    }

    public Long hostUserId() {
        return hostUserId;
    }

    public Long attemptId() {
        return attemptId;
    }

    public RelayRoomStatus status() {
        return status;
    }

    public int totalLaps() {
        return totalLaps;
    }

    public int maxParticipants() {
        return maxParticipants;
    }

    public Integer seatCount() {
        return seatCount;
    }

    public int currentTurnIndex() {
        return currentTurnIndex;
    }

    public Instant turnDeadline() {
        return turnDeadline;
    }

    public UUID currentRunId() {
        return currentRunId;
    }

    public UUID baselineRunId() {
        return baselineRunId;
    }

    public Integer baselinePassed() {
        return baselinePassed;
    }

    public Integer baselineTotal() {
        return baselineTotal;
    }

    public Instant createdAt() {
        return createdAt;
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
        if (!(other instanceof RelayRoom room)) {
            return false;
        }

        return id != null && id.equals(room.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
