package com.promptstudio.relay.service;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.service.AttemptService;
import com.promptstudio.relay.domain.RelayParticipant;
import com.promptstudio.relay.domain.RelayRoom;
import com.promptstudio.relay.domain.RelayRoomStatus;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.domain.RelayTurn;
import com.promptstudio.relay.exception.NotRelayHostException;
import com.promptstudio.relay.exception.RelayNotEnoughParticipantsException;
import com.promptstudio.relay.exception.RelayRoomAlreadyStartedException;
import com.promptstudio.relay.exception.RelayRoomNotFoundException;
import com.promptstudio.relay.repository.RelayParticipantRepository;
import com.promptstudio.relay.repository.RelayRoomRepository;
import com.promptstudio.relay.repository.RelayTurnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 게임 진행의 트랜잭션 경계. {@link RelayGameService}가 트랜잭션을 직접 열지 않는 이유는
 * AttemptService/AttemptWriter의 분리와 같다 — LLM 생성·채점 대기가 수십 초라 그 앞뒤의 짧은
 * DB 작업만 트랜잭션으로 묶어야 하고, 기다리는 동안 커넥션을 물고 있으면 안 된다.
 *
 * <p>채점 이후의 전이(recordGrading·skipGrading)는 방 행에 쓰기 락을 걸고 상태를 재확인한다.
 * 결과 이벤트(rabbit 리스너 스레드)와 채점 마감 스케줄러가 동시에 같은 방을 전진시키려 할 수
 * 있는데, 락 없이 두 트랜잭션이 겹치면 마지막 커밋이 이기면서 턴 인덱스가 두 번 뛰거나 기록된
 * 채점이 null로 덮인다. 진 쪽은 재확인에서 걸러져 no-op이 된다.
 */
@Component
public class RelayGameWriter {

    /**
     * 이 시각까지 채점 결과가 없으면 채점 없이 전진한다. 워커의 좌초 회수(STALE_RUN_TTL 2분)보다
     * 길게 잡아, 회수된 run의 RUNNER_ERROR 결과가 먼저 도착할 기회를 준다.
     */
    static final Duration GRADING_DEADLINE = Duration.ofMinutes(3);

    private static final Logger log = LoggerFactory.getLogger(RelayGameWriter.class);

    private final RelayRoomRepository roomRepository;
    private final RelayParticipantRepository participantRepository;
    private final RelayTurnRepository turnRepository;
    private final RelayRoomViewFactory viewFactory;
    private final AttemptService attemptService;
    private final ApplicationEventPublisher eventPublisher;

    public RelayGameWriter(
            RelayRoomRepository roomRepository,
            RelayParticipantRepository participantRepository,
            RelayTurnRepository turnRepository,
            RelayRoomViewFactory viewFactory,
            AttemptService attemptService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.turnRepository = turnRepository;
        this.viewFactory = viewFactory;
        this.attemptService = attemptService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 주자가 이 시간 안에 프롬프트를 내지 않으면 차례가 스킵된다. AFK 하나가 게임 전체를
     * 인질로 잡지 못하게 하는 안전망이라 게임의 일부다. 방마다 다를 수 있어 방 인스턴스의
     * 설정값(turnTimeLimitSeconds)에서 매번 다시 계산한다.
     */
    private Instant nextInputDeadline(RelayRoom room) {
        return Instant.now().plusSeconds(room.turnTimeLimitSeconds());
    }

    /**
     * 입장을 마감하고 좌석을 확정한 뒤 코드 진화를 담을 어템프트를 만든다.
     *
     * <p>방 행에 쓰기 락을 건다 — 입장(join)이 같은 락을 잡으므로 좌석 확정과 새 입장이 서로
     * 끼어들 수 없다. 락 없이 시작하면 좌석을 세는 사이에 입장한 사람이 좌석 없이 게임에 갇힌다.
     */
    @Transactional
    public RelayRoomView startGame(Long roomId, Long userId) {
        RelayRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RelayRoomNotFoundException(roomId));

        if (!room.isHost(userId)) {
            throw new NotRelayHostException(roomId);
        }

        if (!room.isWaiting()) {
            throw new RelayRoomAlreadyStartedException(roomId);
        }

        List<RelayParticipant> participants = participantRepository.findByRoomIdOrderByJoinedAt(roomId);

        if (participants.size() < RelayRoom.MIN_PARTICIPANTS) {
            throw new RelayNotEnoughParticipantsException(roomId, RelayRoom.MIN_PARTICIPANTS);
        }

        for (int seat = 0; seat < participants.size(); seat++) {
            participants.get(seat).assignSeat(seat);
            participantRepository.save(participants.get(seat));
        }

        // 어템프트는 방장 소유로 만든다. 릴레이 도메인의 인가를 통과한 요청만 여기 닿으므로,
        // 어템프트 쪽에서는 방장의 평범한 풀이로 보인다.
        AttemptView attempt = attemptService.startAttempt(
                room.problemId(), AttemptOwner.user(room.hostUserId()), null);

        room.startGame(participants.size(), attempt.id(), nextInputDeadline(room));
        roomRepository.save(room);

        log.info("[RELAY] game started | roomId={} | attemptId={} | seats={} | laps={}",
                roomId, attempt.id(), participants.size(), room.totalLaps());

        RelayRoomView view = viewFactory.toView(room, participants);
        eventPublisher.publishEvent(new RelayRoomChanged(view));

        return view;
    }

    /**
     * 베이스라인 채점 요청을 방에 기록한다. 요청 자체(rabbit 발행)는 호출자가 커밋 밖에서 한다.
     */
    @Transactional
    public void attachBaselineRun(Long roomId, UUID runId) {
        RelayRoom room = getRoomForUpdate(roomId);

        room.attachBaselineRun(runId);
        roomRepository.save(room);

        log.info("[RELAY] baseline grading queued | roomId={} | runId={}", roomId, runId);
    }

    /**
     * 턴을 원자적으로 붙잡고 "생성 중" 상태를 접속자들에게 알린다.
     */
    @Transactional
    public boolean claimTurn(Long roomId, int expectedTurnIndex) {
        if (!roomRepository.tryClaimTurn(roomId, expectedTurnIndex)) {
            return false;
        }

        eventPublisher.publishEvent(new RelayRoomChanged(viewFactory.toView(getRoom(roomId))));

        return true;
    }

    /**
     * 생성이 끝난 턴을 기록하고 채점 대기로 넘긴다. 좌석은 아직 전진하지 않는다 —
     * 다음 주자는 채점 결과를 보고 시작한다.
     */
    @Transactional
    public RelayTurnResult recordTurnSuccess(
            Long roomId, int turnIndex, Long authorUserId, AttemptView attempt, Instant claimedAt) {
        RelayRoom room = getRoom(roomId);

        int attemptTurnOrdinal = attempt.turns().size() - 1;
        AttemptView.TurnView generated = attempt.turns().get(attemptTurnOrdinal);

        turnRepository.save(RelayTurn.completed(roomId, turnIndex, authorUserId, attemptTurnOrdinal, claimedAt));
        room.finishGeneration(Instant.now().plus(GRADING_DEADLINE));
        roomRepository.save(room);

        RelayTurnSummary summary = new RelayTurnSummary(
                turnIndex,
                turnIndex % room.seatCount(),
                turnIndex / room.seatCount(),
                authorUserId,
                generated.aiSummary(),
                changedPathsOf(generated)
        );
        RelayRoomView view = viewFactory.toView(room);

        eventPublisher.publishEvent(new RelayTurnFinished(view, summary));

        log.info("[RELAY] turn finished, grading next | roomId={} | turnIndex={} | authorUserId={}",
                roomId, turnIndex, authorUserId);

        return new RelayTurnResult(view, summary);
    }

    /**
     * 생성 실패. 좌석을 되돌려 같은 주자가 재시도할 수 있게 하고, 대기자들에게 알린다.
     */
    @Transactional
    public void recordTurnFailure(Long roomId, int turnIndex, Long authorUserId) {
        RelayRoom room = getRoom(roomId);

        room.failTurn(nextInputDeadline(room));
        roomRepository.save(room);

        eventPublisher.publishEvent(new RelayTurnFailed(viewFactory.toView(room), turnIndex, authorUserId));

        log.warn("[RELAY] turn failed, seat kept | roomId={} | turnIndex={} | authorUserId={}",
                roomId, turnIndex, authorUserId);
    }

    /**
     * 프롬프트 반려. 좌석은 생성 실패처럼 되돌리지만 마감은 새로 주지 않는다 — 새 마감은
     * 생성이 주자의 시간을 잡아먹은 502를 보상하는 장치인데, 반려는 주자 입력의 문제라
     * 보상하면 무관한 프롬프트 반복 전송으로 시간을 무한정 벌 수 있다.
     */
    @Transactional
    public void recordTurnRejection(Long roomId, int turnIndex, Long authorUserId) {
        RelayRoom room = getRoom(roomId);

        room.rejectTurn();
        roomRepository.save(room);

        eventPublisher.publishEvent(new RelayTurnFailed(viewFactory.toView(room), turnIndex, authorUserId));

        log.info("[RELAY] turn rejected, deadline kept | roomId={} | turnIndex={} | authorUserId={}",
                roomId, turnIndex, authorUserId);
    }

    /**
     * 이번 턴의 채점 run을 방과 턴 양쪽에 기록한다. 방의 current_run_id는 결과 이벤트가 방을
     * 되찾는 열쇠고, 턴의 run_id는 지난 채점을 되짚는 기록이다.
     */
    @Transactional
    public RelayRoomView attachGradingRun(Long roomId, int turnIndex, UUID runId) {
        RelayRoom room = getRoomForUpdate(roomId);

        room.attachGradingRun(runId);
        roomRepository.save(room);

        RelayTurn turn = getTurn(roomId, turnIndex);
        turn.attachRun(runId);
        turnRepository.save(turn);

        RelayRoomView view = viewFactory.toView(room);
        eventPublisher.publishEvent(new RelayGradingStarted(view, turnIndex, runId));

        return view;
    }

    /**
     * 베이스라인 결과를 기록한다.
     *
     * @return 미뤄 둔 턴 채점 요청이 있는가 — 베이스라인이 아직 도는 중에 첫 턴 생성이 끝나면
     *         run 충돌 때문에 턴 채점을 못 걸고 기다리는데, 그 재시도 시점이 바로 지금이다
     */
    @Transactional
    public boolean recordBaseline(Long roomId, UUID runId, Integer passed, Integer total) {
        RelayRoom room = getRoomForUpdate(roomId);

        if (!room.isBaselineRun(runId)) {
            log.info("[RELAY] 베이스라인이 아닌 run 결과를 무시합니다 | roomId={} | runId={}", roomId, runId);

            return false;
        }

        room.recordBaseline(passed, total);
        roomRepository.save(room);

        eventPublisher.publishEvent(new RelayRoomChanged(viewFactory.toView(room)));

        log.info("[RELAY] baseline recorded | roomId={} | passed={} | total={}", roomId, passed, total);

        return room.status() == RelayRoomStatus.TURN_GRADING
                && getTurn(roomId, room.currentTurnIndex()).runId() == null;
    }

    /**
     * 턴 채점 결과를 기록하고 좌석을 전진시킨다.
     *
     * @return 전진 후의 방 스냅샷. 이미 다른 경로(마감 스케줄러)가 전진시킨 뒤면 null
     */
    @Transactional
    public RelayRoomView recordGrading(Long roomId, UUID runId, RelayGradeTally tally) {
        RelayRoom room = getRoomForUpdate(roomId);

        // 마감 스케줄러가 먼저 전진시켰으면 currentRunId가 비어 있다. 늦은 결과는 버린다 —
        // 이미 다음 주자가 시작했는데 지난 턴의 점수가 바뀌면 화면과 델타가 어긋난다.
        if (room.status() != RelayRoomStatus.TURN_GRADING || !runId.equals(room.currentRunId())) {
            log.info("[RELAY] 늦거나 어긋난 채점 결과를 무시합니다 | roomId={} | runId={} | status={}",
                    roomId, runId, room.status());

            return null;
        }

        int turnIndex = room.currentTurnIndex();
        RelayTurn turn = getTurn(roomId, turnIndex);
        turn.recordGrading(tally.passed(), tally.total());
        turnRepository.save(turn);

        Integer delta = RelayScoring.delta(tally.passed(), RelayScoring.previousPassed(
                turnRepository.findByRoomIdOrderByTurnIndex(roomId), turnIndex, room.baselinePassed()));

        room.advanceAfterGrading(nextInputDeadline(room));
        roomRepository.save(room);

        eventPublisher.publishEvent(new RelayGradingFinished(
                viewFactory.toView(room),
                RelayGradingFinished.Outcome.graded(turnIndex, turn.authorUserId(), tally, delta)));

        log.info("[RELAY] grading finished | roomId={} | turnIndex={} | passed={}/{} | delta={} | nextStatus={}",
                roomId, turnIndex, tally.passed(), tally.total(), delta, room.status());

        // 다음 좌석이 이탈자면 기다릴 이유가 없다 — 연달아 건너뛴다.
        skipAbsentSeats(room);

        return viewFactory.toView(room);
    }

    /**
     * 채점을 포기하고 전진한다. 마감 초과·요청 실패가 이 경로다. 한 번의 채점 사고로 게임 전체가
     * 멈추면 안 된다 — 이 턴의 점수만 비운다.
     *
     * @return 전진 후의 방 스냅샷. 이미 다른 경로가 전진시킨 뒤면 null
     */
    @Transactional
    public RelayRoomView skipGrading(Long roomId, String reason) {
        RelayRoom room = getRoomForUpdate(roomId);

        if (room.status() != RelayRoomStatus.TURN_GRADING) {
            return null;
        }

        int turnIndex = room.currentTurnIndex();
        RelayTurn turn = getTurn(roomId, turnIndex);
        Long authorUserId = turn.authorUserId();

        room.advanceAfterGrading(nextInputDeadline(room));
        roomRepository.save(room);

        eventPublisher.publishEvent(new RelayGradingFinished(
                viewFactory.toView(room), RelayGradingFinished.Outcome.skipped(turnIndex, authorUserId)));

        log.warn("[RELAY] grading skipped | roomId={} | turnIndex={} | reason={}", roomId, turnIndex, reason);

        skipAbsentSeats(room);

        return viewFactory.toView(room);
    }

    /**
     * 이탈한 주자의 차례를 건너뛴다. 참가자가 이탈한 직후 그 사람이 현재 좌석이면 즉시 부른다 —
     * 입력 마감까지 기다리는 것은 있는 사람의 몫이지, 없는 걸 아는 사람을 기다리는 이유가 없다.
     *
     * @return 스킵이 일어났으면 그 후의 방 스냅샷, 아니면 null
     */
    @Transactional
    public RelayRoomView skipAbsentTurns(Long roomId) {
        RelayRoom room = getRoomForUpdate(roomId);

        if (!skipAbsentSeats(room)) {
            return null;
        }

        return viewFactory.toView(room);
    }

    /**
     * 입력 마감을 넘긴 방의 현재 턴을 건너뛴다. 마감 스케줄러가 부른다.
     *
     * <p>마감은 락 안에서 재확인한다 — 스케줄러가 만료 목록을 읽은 뒤 이 락을 잡기 전에
     * 주자가 턴을 완료해 새 마감이 걸렸을 수 있다.
     *
     * @return 스킵 후의 방 스냅샷. 이미 상태가 바뀌어 스킵할 것이 없으면 null
     */
    @Transactional
    public RelayRoomView skipExpiredTurn(Long roomId) {
        RelayRoom room = getRoomForUpdate(roomId);

        if (!room.isTurnInputExpired(Instant.now())) {
            return null;
        }

        skipOnce(room, participantRepository.findByRoomIdOrderByJoinedAt(roomId), "input deadline exceeded");
        skipAbsentSeats(room);

        return viewFactory.toView(room);
    }

    /**
     * 현재 좌석이 이탈자인 동안 연달아 건너뛴다. PLAYING으로 들어서는 모든 전이 뒤에 불러야
     * 하고, room은 이 트랜잭션이 소유한 상태여야 한다.
     *
     * @return 하나라도 건너뛰었는가
     */
    private boolean skipAbsentSeats(RelayRoom room) {
        List<RelayParticipant> participants = participantRepository.findByRoomIdOrderByJoinedAt(room.id());
        boolean skippedAny = false;

        while (room.status() == RelayRoomStatus.PLAYING && isSeatAbsent(participants, room.currentSeat())) {
            skipOnce(room, participants, "runner left");
            skippedAny = true;
        }

        return skippedAny;
    }

    private void skipOnce(RelayRoom room, List<RelayParticipant> participants, String reason) {
        int turnIndex = room.currentTurnIndex();
        Long authorUserId = userAtSeat(participants, room.currentSeat());

        turnRepository.save(RelayTurn.skipped(room.id(), turnIndex, authorUserId));
        room.skipTurn(nextInputDeadline(room));
        roomRepository.save(room);

        eventPublisher.publishEvent(new RelayTurnSkipped(
                viewFactory.toView(room, participants), turnIndex, authorUserId));

        log.info("[RELAY] turn skipped | roomId={} | turnIndex={} | authorUserId={} | reason={} | nextStatus={}",
                room.id(), turnIndex, authorUserId, reason, room.status());
    }

    private boolean isSeatAbsent(List<RelayParticipant> participants, Integer seat) {
        for (RelayParticipant participant : participants) {
            if (seat != null && seat.equals(participant.seatOrder())) {
                return participant.hasLeft();
            }
        }

        return false;
    }

    private Long userAtSeat(List<RelayParticipant> participants, Integer seat) {
        for (RelayParticipant participant : participants) {
            if (seat != null && seat.equals(participant.seatOrder())) {
                return participant.userId();
            }
        }

        throw new IllegalStateException("No participant at seat " + seat);
    }

    /** 피드백까지 준비 완료. 이때에야 게임이 닫힌다. */
    @Transactional
    public RelayRoomView finishGame(Long roomId) {
        RelayRoom room = getRoomForUpdate(roomId);

        room.finishGame();
        roomRepository.save(room);

        RelayRoomView view = viewFactory.toView(room);
        eventPublisher.publishEvent(new RelayFeedbackReady(view));

        log.info("[RELAY] game finished | roomId={}", roomId);

        return view;
    }

    private RelayRoom getRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RelayRoomNotFoundException(roomId));
    }

    private RelayRoom getRoomForUpdate(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RelayRoomNotFoundException(roomId));
    }

    private RelayTurn getTurn(Long roomId, int turnIndex) {
        return turnRepository.findByRoomIdAndTurnIndex(roomId, turnIndex)
                .orElseThrow(() -> new IllegalStateException(
                        "Relay turn not found: roomId=" + roomId + ", turnIndex=" + turnIndex));
    }

    private List<String> changedPathsOf(AttemptView.TurnView turn) {
        List<String> paths = new ArrayList<>();

        for (FileChange change : turn.changes()) {
            paths.add(change.path());
        }

        return paths;
    }
}
