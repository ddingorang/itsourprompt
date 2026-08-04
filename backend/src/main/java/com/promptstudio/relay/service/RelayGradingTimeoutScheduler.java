package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayRoom;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.repository.RelayRoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 마감을 넘긴 방을 전진시키는 안전망. 두 가지 마감을 본다.
 *
 * <p><b>채점 마감(TURN_GRADING)</b>: 채점은 외부 비동기(워커)에 걸려 있어서 결과 이벤트가 영영
 * 오지 않을 수 있다 — 워커 전멸, 결과 메시지 유실, 리스너 처리 실패. 회수하지 않으면 방이
 * 갇혀 게임이 끝나지 않는다.
 *
 * <p><b>입력 마감(PLAYING)</b>: 주자가 프롬프트를 내지 않는다(AFK). 이탈 신고 없이 잠수한
 * 사람 하나가 게임 전체를 인질로 잡지 못하게 그 턴을 건너뛴다. 명시적으로 이탈한 주자는
 * 마감을 기다리지 않고 즉시 스킵된다(RelayGameWriter.skipAbsentTurns).
 */
@Component
public class RelayGradingTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(RelayGradingTimeoutScheduler.class);

    private final RelayRoomRepository roomRepository;
    private final RelayGameWriter gameWriter;
    private final RelayFeedbackService feedbackService;

    public RelayGradingTimeoutScheduler(
            RelayRoomRepository roomRepository,
            RelayGameWriter gameWriter,
            RelayFeedbackService feedbackService
    ) {
        this.roomRepository = roomRepository;
        this.gameWriter = gameWriter;
        this.feedbackService = feedbackService;
    }

    @Scheduled(fixedDelay = 15_000)
    public void reapExpiredDeadlines() {
        for (RelayRoom room : roomRepository.findExpiredGrading(Instant.now())) {
            // 결과 이벤트와 동시에 도착하면 한쪽만 이긴다 — skipGrading이 락을 잡고
            // 상태를 재확인하므로, 진 쪽은 null을 받고 물러난다.
            reap(room.id(), () -> gameWriter.skipGrading(room.id(), "grading deadline exceeded"));
        }

        for (RelayRoom room : roomRepository.findExpiredTurnInput(Instant.now())) {
            // 목록을 읽은 뒤 주자가 턴을 완료했을 수 있다 — skipExpiredTurn이 락 안에서
            // 마감을 재확인하고, 이미 새 마감이 걸려 있으면 null을 받고 물러난다.
            reap(room.id(), () -> gameWriter.skipExpiredTurn(room.id()));
        }
    }

    private void reap(Long roomId, java.util.function.Supplier<RelayRoomView> action) {
        try {
            RelayRoomView view = action.get();

            if (view != null) {
                feedbackService.maybeStartFeedback(view);
            }
        } catch (RuntimeException exception) {
            log.error("[RELAY] 마감 회수에 실패했습니다 | roomId={}", roomId, exception);
        }
    }
}
