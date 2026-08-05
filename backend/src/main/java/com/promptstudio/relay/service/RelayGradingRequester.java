package com.promptstudio.relay.service;

import com.promptstudio.attempt.domain.CodeRunView;
import com.promptstudio.attempt.exception.CodeRunInProgressException;
import com.promptstudio.attempt.service.CodeRunService;
import com.promptstudio.relay.domain.RelayRoom;
import com.promptstudio.relay.domain.RelayRoomStatus;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.domain.RelayTurn;
import com.promptstudio.relay.exception.RelayRoomNotFoundException;
import com.promptstudio.relay.repository.RelayRoomRepository;
import com.promptstudio.relay.repository.RelayTurnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 이번 턴의 자동 채점을 큐에 건다. 트랜잭션 밖에서 동작한다 — {@link CodeRunService}는 QUEUED
 * 행이 커밋된 뒤에 메시지를 발행해야 해서 트랜잭션을 열지 않는데, 열린 트랜잭션 안에서 부르면
 * 그 규칙이 깨져 워커가 아직 보이지 않는 행을 UPDATE 하려 든다.
 */
@Component
public class RelayGradingRequester {

    private static final Logger log = LoggerFactory.getLogger(RelayGradingRequester.class);

    private final RelayRoomRepository roomRepository;
    private final RelayTurnRepository turnRepository;
    private final RelayRoomViewFactory viewFactory;
    private final CodeRunService codeRunService;
    private final RelayGameWriter gameWriter;

    public RelayGradingRequester(
            RelayRoomRepository roomRepository,
            RelayTurnRepository turnRepository,
            RelayRoomViewFactory viewFactory,
            CodeRunService codeRunService,
            RelayGameWriter gameWriter
    ) {
        this.roomRepository = roomRepository;
        this.turnRepository = turnRepository;
        this.viewFactory = viewFactory;
        this.codeRunService = codeRunService;
        this.gameWriter = gameWriter;
    }

    /**
     * 채점 대기(TURN_GRADING) 중인 방의 현재 턴을 채점한다.
     *
     * <p>세 갈래로 끝난다. (1) 큐잉 성공 — run을 기록하고 grading.started가 나간다.
     * (2) run 충돌 — 베이스라인이 아직 도는 중이다. 그 결과 이벤트가 도착할 때 재시도되고,
     * 그마저 없으면 채점 마감 스케줄러가 전진시키므로 여기서는 기다리기만 한다.
     * (3) 그 외 실패(브로커 다운 등) — 채점을 포기하고 전진한다. 한 번의 채점 사고로 게임이
     * 멈추면 안 된다.
     *
     * @return 처리 후의 방 스냅샷
     */
    public RelayRoomView requestGrading(Long roomId) {
        RelayRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RelayRoomNotFoundException(roomId));

        if (room.status() != RelayRoomStatus.TURN_GRADING) {
            return viewFactory.toView(room);
        }

        RelayTurn turn = turnRepository.findByRoomIdAndTurnIndex(roomId, room.currentTurnIndex())
                .orElseThrow(() -> new IllegalStateException(
                        "Relay turn not found: roomId=" + roomId + ", turnIndex=" + room.currentTurnIndex()));

        try {
            CodeRunView run = codeRunService.requestRun(room.attemptId(), turn.attemptTurnOrdinal());

            return gameWriter.attachGradingRun(roomId, turn.turnIndex(), run.id());
        } catch (CodeRunInProgressException exception) {
            log.info("[RELAY] 베이스라인 채점이 끝나기를 기다립니다 | roomId={} | turnIndex={}",
                    roomId, turn.turnIndex());

            return viewFactory.toView(room);
        } catch (RuntimeException exception) {
            log.error("[RELAY] 채점 요청에 실패해 채점 없이 전진합니다 | roomId={} | turnIndex={}",
                    roomId, turn.turnIndex(), exception);

            RelayRoomView advanced = gameWriter.skipGrading(roomId, "grading request failed");

            return advanced != null ? advanced : viewFactory.toView(room);
        }
    }
}
