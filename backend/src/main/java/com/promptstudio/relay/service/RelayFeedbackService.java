package com.promptstudio.relay.service;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.exception.AttemptHasNoTurnsException;
import com.promptstudio.attempt.service.AttemptService;
import com.promptstudio.relay.domain.RelayRoom;
import com.promptstudio.relay.domain.RelayRoomStatus;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.domain.RelayTurn;
import com.promptstudio.relay.exception.NotRelayParticipantException;
import com.promptstudio.relay.exception.RelayGameNotPlayingException;
import com.promptstudio.relay.exception.RelayGameNotStartedException;
import com.promptstudio.relay.exception.RelayRoomNotFoundException;
import com.promptstudio.relay.repository.RelayParticipantRepository;
import com.promptstudio.relay.repository.RelayRoomRepository;
import com.promptstudio.relay.repository.RelayTurnRepository;
import com.promptstudio.user.domain.User;
import com.promptstudio.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 마지막 채점이 끝난 방의 피드백 생성과 조회.
 *
 * <p>생성은 어템프트 제출(LLM, 수십 초)이라 반드시 별도 executor에서 돈다 — 트리거 지점이
 * rabbit 리스너 스레드(마지막 채점 결과)와 스케줄러 스레드인데, 거기서 블로킹하면 다른 방의
 * 채점 결과 처리가 밀린다.
 */
@Service
public class RelayFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(RelayFeedbackService.class);

    private final RelayRoomRepository roomRepository;
    private final RelayParticipantRepository participantRepository;
    private final RelayTurnRepository turnRepository;
    private final UserRepository userRepository;
    private final RelayRoomViewFactory viewFactory;
    private final RelayGameWriter gameWriter;
    private final AttemptService attemptService;
    private final ApplicationEventPublisher eventPublisher;
    private final Executor feedbackExecutor;

    public RelayFeedbackService(
            RelayRoomRepository roomRepository,
            RelayParticipantRepository participantRepository,
            RelayTurnRepository turnRepository,
            UserRepository userRepository,
            RelayRoomViewFactory viewFactory,
            RelayGameWriter gameWriter,
            AttemptService attemptService,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("relayFeedbackExecutor") Executor feedbackExecutor
    ) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.turnRepository = turnRepository;
        this.userRepository = userRepository;
        this.viewFactory = viewFactory;
        this.gameWriter = gameWriter;
        this.attemptService = attemptService;
        this.eventPublisher = eventPublisher;
        this.feedbackExecutor = feedbackExecutor;
    }

    /**
     * 채점·스킵 전진의 뒤처리. 마지막 턴이었으면 피드백 생성이 시작된다.
     *
     * <p>호출자가 트랜잭션 안이면(이탈 즉시 스킵 경로) 커밋 후로 미룬다 — executor 작업이
     * 커밋 전에 새 트랜잭션에서 방을 읽으면 아직 FEEDBACK_GENERATING이 아니어서 조용히
     * 반환하고, 그 방은 아무도 다시 깨우지 않는다.
     */
    public void maybeStartFeedback(RelayRoomView room) {
        if (room.status() != RelayRoomStatus.FEEDBACK_GENERATING) {
            return;
        }

        Long roomId = room.id();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    feedbackExecutor.execute(() -> generate(roomId));
                }
            });

            return;
        }

        feedbackExecutor.execute(() -> generate(roomId));
    }

    /**
     * 실패한 피드백 생성의 재시도. 참가자 누구나 걸 수 있다 — 방장이 이탈했을 수 있는데
     * 방장만 재시도할 수 있으면 게임이 영영 닫히지 않는다.
     */
    public void retryFeedback(Long roomId, Long userId) {
        requireParticipant(roomId, userId);

        RelayRoom room = getRoom(roomId);

        // 이미 끝난 방이면 할 일이 없다. 재시도 버튼 연타가 에러로 튀지 않게 조용히 받는다.
        if (room.status() == RelayRoomStatus.FINISHED) {
            return;
        }

        if (room.status() != RelayRoomStatus.FEEDBACK_GENERATING) {
            throw new RelayGameNotPlayingException(roomId, room.status());
        }

        feedbackExecutor.execute(() -> generate(roomId));
    }

    /**
     * 게임 피드백. 어템프트의 턴별 피드백에 작성자와 채점 점수를 이어붙인다 —
     * 릴레이에서 턴별 피드백은 곧 주자별 피드백이다.
     */
    public RelayFeedbackView getFeedback(Long roomId, Long userId) {
        requireParticipant(roomId, userId);

        RelayRoom room = getRoom(roomId);

        if (room.attemptId() == null) {
            throw new RelayGameNotStartedException(roomId);
        }

        // 제출 전이면 여기서 FeedbackNotFoundException(404)이 난다.
        AttemptView attempt = attemptService.getFeedback(room.attemptId(), AttemptOwner.user(room.hostUserId()));
        List<RelayTurn> turns = turnRepository.findByRoomIdOrderByTurnIndex(roomId);
        Map<Long, String> nicknames = nicknamesOf(turns);

        List<RelayFeedbackView.TurnFeedback> turnFeedbacks = new ArrayList<>();

        for (RelayTurn turn : turns) {
            turnFeedbacks.add(new RelayFeedbackView.TurnFeedback(
                    turn.turnIndex(),
                    turn.turnIndex() % room.seatCount(),
                    turn.turnIndex() / room.seatCount(),
                    turn.authorUserId(),
                    nicknames.get(turn.authorUserId()),
                    feedbackOf(attempt, turn),
                    turn.passedCount(),
                    turn.totalCount(),
                    RelayScoring.delta(turn.passedCount(),
                            RelayScoring.previousPassed(turns, turn.turnIndex(), room.baselinePassed()))
            ));
        }

        return new RelayFeedbackView(attempt.feedback(), turnFeedbacks);
    }

    /**
     * 생성 실패 시 방을 FEEDBACK_GENERATING에 남겨 둔다. FINISHED로 밀어 버리면 피드백 없는
     * 게임이 되고, 실패 원인(일시적 제공자 장애가 대부분)이 사라진 뒤에도 되살릴 길이 없다.
     * 대신 feedback.failed를 알리고 재시도 API를 기다린다.
     */
    private void generate(Long roomId) {
        RelayRoom room = getRoom(roomId);

        if (room.status() != RelayRoomStatus.FEEDBACK_GENERATING) {
            return;
        }

        log.info("[RELAY] feedback generation started | roomId={} | attemptId={}", roomId, room.attemptId());

        try {
            // 이미 제출된 어템프트면 저장된 피드백을 그대로 돌려주므로 재시도가 안전하다.
            attemptService.submit(room.attemptId(), AttemptOwner.user(room.hostUserId()));
            gameWriter.finishGame(roomId);
        } catch (AttemptHasNoTurnsException exception) {
            // 모든 턴이 스킵된 게임(전원 이탈·전원 AFK)이다. 피드백을 만들 재료가 없고
            // 재시도해도 같으므로, 피드백 없이 게임을 닫는다 — FEEDBACK_GENERATING에
            // 남겨 두면 영영 끝나지 않는 방이 된다.
            log.warn("[RELAY] 턴 없이 끝난 게임을 피드백 없이 닫습니다 | roomId={}", roomId);

            gameWriter.finishGame(roomId);
        } catch (RuntimeException exception) {
            log.error("[RELAY] feedback generation failed | roomId={}", roomId, exception);

            eventPublisher.publishEvent(new RelayFeedbackFailed(viewFactory.toView(room)));
        }
    }

    private String feedbackOf(AttemptView attempt, RelayTurn turn) {
        if (turn.attemptTurnOrdinal() == null) {
            return null;
        }

        return attempt.turns().get(turn.attemptTurnOrdinal()).feedback();
    }

    private Map<Long, String> nicknamesOf(List<RelayTurn> turns) {
        List<Long> userIds = new ArrayList<>();

        for (RelayTurn turn : turns) {
            if (!userIds.contains(turn.authorUserId())) {
                userIds.add(turn.authorUserId());
            }
        }

        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> nicknames = new HashMap<>();

        for (User user : userRepository.findAllById(userIds)) {
            nicknames.put(user.id(), user.nickname());
        }

        return nicknames;
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
