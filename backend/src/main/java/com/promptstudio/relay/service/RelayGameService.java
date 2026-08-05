package com.promptstudio.relay.service;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.CodeRunView;
import com.promptstudio.attempt.exception.PromptScopeRejectedException;
import com.promptstudio.attempt.service.AttemptService;
import com.promptstudio.attempt.service.CodeRunService;
import com.promptstudio.relay.domain.RelayParticipant;
import com.promptstudio.relay.domain.RelayRoom;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.domain.RelayTurn;
import com.promptstudio.relay.exception.NotRelayParticipantException;
import com.promptstudio.relay.exception.RelayGameNotPlayingException;
import com.promptstudio.relay.exception.RelayGameNotStartedException;
import com.promptstudio.relay.exception.RelayNotYourTurnException;
import com.promptstudio.relay.exception.RelayRoomNotFoundException;
import com.promptstudio.relay.repository.RelayParticipantRepository;
import com.promptstudio.relay.repository.RelayRoomRepository;
import com.promptstudio.relay.repository.RelayTurnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 게임 진행: 시작, 턴 전송, 현재 코드·턴 이력 조회.
 *
 * <p>인가가 두 층이다. "지금 네 순서인가"는 이 서비스가 판정하고, 판정을 통과하면 방장 신원으로
 * 어템프트 서비스에 위임한다. 어템프트 입장에서는 평범한 자기 소유 어템프트라 기존 가드
 * (CodeGenerationGuard의 어템프트당 직렬화, 멱등 처리)가 그대로 동작한다.
 *
 * <p>이 클래스는 트랜잭션을 직접 열지 않는다(조회 제외). LLM 생성이 수십 초라, 짧은 DB 작업은
 * {@link RelayGameWriter}가 각각의 트랜잭션으로 처리한다.
 */
@Service
public class RelayGameService {

    private static final Logger log = LoggerFactory.getLogger(RelayGameService.class);

    private final RelayRoomRepository roomRepository;
    private final RelayParticipantRepository participantRepository;
    private final RelayTurnRepository turnRepository;
    private final RelayGameWriter gameWriter;
    private final RelayGradingRequester gradingRequester;
    private final AttemptService attemptService;
    private final CodeRunService codeRunService;

    public RelayGameService(
            RelayRoomRepository roomRepository,
            RelayParticipantRepository participantRepository,
            RelayTurnRepository turnRepository,
            RelayGameWriter gameWriter,
            RelayGradingRequester gradingRequester,
            AttemptService attemptService,
            CodeRunService codeRunService
    ) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.turnRepository = turnRepository;
        this.gameWriter = gameWriter;
        this.gradingRequester = gradingRequester;
        this.attemptService = attemptService;
        this.codeRunService = codeRunService;
    }

    /**
     * 시작(좌석 확정 + 어템프트 생성)은 writer의 트랜잭션이 하고, 그 커밋 뒤에 스켈레톤의
     * 베이스라인 채점을 건다. 커밋 전에 걸면 QUEUED 행이 커밋되기 전에 메시지가 발행되어
     * 워커가 아직 보이지 않는 행을 UPDATE 하려 든다.
     */
    public RelayRoomView start(Long roomId, Long userId) {
        RelayRoomView view = gameWriter.startGame(roomId, userId);

        requestBaseline(roomId);

        return view;
    }

    /**
     * 베이스라인은 첫 주자의 점수를 재는 기준선이다. 실패해도 게임은 시작된다 — 첫 턴의 델타만
     * null이 된다. 시작을 채점 결과에 묶으면 워커 장애가 게임 개설 장애로 번진다.
     */
    private void requestBaseline(Long roomId) {
        RelayRoom room = getRoom(roomId);

        try {
            // 턴이 없는 어템프트의 실행 = 시작 스켈레톤.
            CodeRunView run = codeRunService.requestRun(room.attemptId());
            gameWriter.attachBaselineRun(roomId, run.id());
        } catch (RuntimeException exception) {
            log.warn("[RELAY] 베이스라인 채점 요청에 실패했습니다. 첫 턴 델타 없이 진행합니다 | roomId={}",
                    roomId, exception);
        }
    }

    /**
     * 현재 좌석의 주자가 프롬프트를 전송한다. 생성 성공 후 곧바로 자동 채점을 걸고,
     * 좌석 전진은 채점 결과(또는 마감)가 한다 — 다음 주자는 통과 현황을 보고 시작한다.
     */
    public RelayTurnResult submitTurn(Long roomId, Long userId, String prompt) {
        RelayRoom room = getRoom(roomId);

        if (!room.isPlaying()) {
            throw new RelayGameNotPlayingException(roomId, room.status());
        }

        requireCurrentSeat(room, userId);

        int turnIndex = room.currentTurnIndex();

        // 검사 후 이 줄 사이에 다른 요청이 끼어들 수 있다. 진짜 방어는 이 원자적 전이다 —
        // 위의 검사들은 좋은 에러 메시지를 위한 것이지 동시성 방어가 아니다.
        if (!gameWriter.claimTurn(roomId, turnIndex)) {
            throw new RelayGameNotPlayingException(roomId, room.status());
        }

        Instant claimedAt = Instant.now();

        log.info("[RELAY] turn generation started | roomId={} | turnIndex={} | authorUserId={}",
                roomId, turnIndex, userId);

        AttemptView attempt;

        try {
            attempt = attemptService.addTurn(
                    room.attemptId(), AttemptOwner.user(room.hostUserId()), prompt, null);
        } catch (RuntimeException exception) {
            // 되돌리기가 실패해도 원 예외를 가리지 않는다 — 주자는 502/504를 받아야 재시도한다.
            try {
                // 반려(문제와 무관한 프롬프트)는 주자 입력의 문제라 마감을 새로 주지 않는다.
                // 새 마감은 생성 실패(502 등)가 잡아먹은 시간을 보상하는 장치다.
                if (exception instanceof PromptScopeRejectedException) {
                    gameWriter.recordTurnRejection(roomId, turnIndex, userId);
                } else {
                    gameWriter.recordTurnFailure(roomId, turnIndex, userId);
                }
            } catch (RuntimeException revertFailure) {
                exception.addSuppressed(revertFailure);
            }

            throw exception;
        }

        RelayTurnResult result = gameWriter.recordTurnSuccess(roomId, turnIndex, userId, attempt, claimedAt);
        RelayRoomView afterGradingRequest = gradingRequester.requestGrading(roomId);

        return new RelayTurnResult(afterGradingRequest, result.summary());
    }

    /**
     * 방의 현재 코드. 참가자 전원이 볼 수 있다 — 다음 주자는 이 코드를 보고 프롬프트를 궁리한다.
     */
    @Transactional(readOnly = true)
    public AttemptView getCode(Long roomId, Long userId) {
        RelayRoom room = getRoom(roomId);

        requireParticipant(roomId, userId);

        if (room.attemptId() == null) {
            throw new RelayGameNotStartedException(roomId);
        }

        return attemptService.getAttempt(room.attemptId(), AttemptOwner.user(room.hostUserId()));
    }

    /**
     * 턴 이력과 점수. 재접속한 참가자가 스코어보드를 복원하는 경로다 — grading.finished
     * 브로드캐스트를 놓친 몫을 여기서 되찾는다.
     */
    @Transactional(readOnly = true)
    public List<RelayTurnRecord> getTurns(Long roomId, Long userId) {
        RelayRoom room = getRoom(roomId);

        requireParticipant(roomId, userId);

        List<RelayTurn> turns = turnRepository.findByRoomIdOrderByTurnIndex(roomId);
        List<RelayTurnRecord> records = new ArrayList<>();

        for (RelayTurn turn : turns) {
            records.add(new RelayTurnRecord(
                    turn.turnIndex(),
                    turn.turnIndex() % room.seatCount(),
                    turn.turnIndex() / room.seatCount(),
                    turn.authorUserId(),
                    turn.passedCount(),
                    turn.totalCount(),
                    RelayScoring.delta(turn.passedCount(),
                            RelayScoring.previousPassed(turns, turn.turnIndex(), room.baselinePassed())),
                    turn.isSkipped(),
                    turn.startedAt(),
                    turn.finishedAt()
            ));
        }

        return records;
    }

    /**
     * 좌석 검증은 이탈 여부를 보지 않는다 — 이탈했던 주자가 돌아와 자기 턴을 치는 것은 허용이다.
     * 좌석이 없는 참가자는 있을 수 없다(시작 시 전원에게 부여).
     */
    private void requireCurrentSeat(RelayRoom room, Long userId) {
        RelayParticipant participant = participantRepository.findByRoomIdAndUserId(room.id(), userId)
                .orElseThrow(() -> new NotRelayParticipantException(room.id()));

        if (!room.currentSeat().equals(participant.seatOrder())) {
            throw new RelayNotYourTurnException(room.id(), room.currentSeat());
        }
    }

    private void requireParticipant(Long roomId, Long userId) {
        if (participantRepository.findByRoomIdAndUserId(roomId, userId).isEmpty()) {
            throw new NotRelayParticipantException(roomId);
        }
    }

    private RelayRoom getRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RelayRoomNotFoundException(roomId));
    }
}
