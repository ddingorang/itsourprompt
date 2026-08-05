package com.promptstudio.relay.service;

import com.promptstudio.attempt.service.CodeRunFinishedEvent;
import com.promptstudio.relay.domain.RelayRoom;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.repository.RelayRoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 워커의 채점 결과를 릴레이 진행으로 번역한다. rabbit 리스너 스레드에서 실행되므로 여기서는
 * 짧은 DB 작업만 하고, 피드백 생성(LLM, 수십 초)은 {@link RelayFeedbackService}가 별도
 * executor로 넘긴다 — 리스너 스레드를 물고 있으면 다른 채점 결과가 밀린다.
 */
@Component
public class RelayCodeRunListener {

    private static final Logger log = LoggerFactory.getLogger(RelayCodeRunListener.class);

    private final RelayRoomRepository roomRepository;
    private final RelayGameWriter gameWriter;
    private final RelayGradingRequester gradingRequester;
    private final RelayFeedbackService feedbackService;

    public RelayCodeRunListener(
            RelayRoomRepository roomRepository,
            RelayGameWriter gameWriter,
            RelayGradingRequester gradingRequester,
            RelayFeedbackService feedbackService
    ) {
        this.roomRepository = roomRepository;
        this.gameWriter = gameWriter;
        this.gradingRequester = gradingRequester;
        this.feedbackService = feedbackService;
    }

    /**
     * 예외를 밖으로 던지지 않는다. 던지면 rabbit 메시지가 재전달되는데 applyResult는 멱등이라
     * 두 번째 처리에서 이벤트가 발행되지 않고, 결국 릴레이만 결과를 영영 놓친다. 여기서 삼키면
     * 채점 마감 스케줄러가 그 방을 채점 없이 전진시켜 게임은 계속된다.
     */
    @EventListener
    public void onCodeRunFinished(CodeRunFinishedEvent event) {
        UUID runId = event.result().runId();

        try {
            handle(runId, event);
        } catch (RuntimeException exception) {
            log.error("[RELAY] 채점 결과 처리에 실패했습니다. 마감 스케줄러가 회수합니다 | runId={}",
                    runId, exception);
        }
    }

    private void handle(UUID runId, CodeRunFinishedEvent event) {
        Optional<RelayRoom> found = roomRepository.findByCurrentRunId(runId);

        // 릴레이 방이 없는 일반 어템프트의 실행이다.
        if (found.isEmpty()) {
            return;
        }

        RelayRoom room = found.get();
        RelayGradeTally tally = RelayGradeTally.from(event.result());

        if (room.isBaselineRun(runId)) {
            boolean gradingPending = gameWriter.recordBaseline(room.id(), runId, tally.passed(), tally.total());

            // 베이스라인이 도는 중에 첫 턴 생성이 끝나 채점을 못 걸고 있었다 — 지금이 재시도 시점이다.
            if (gradingPending) {
                gradingRequester.requestGrading(room.id());
            }

            return;
        }

        RelayRoomView view = gameWriter.recordGrading(room.id(), runId, tally);

        if (view != null) {
            feedbackService.maybeStartFeedback(view);
        }
    }
}
