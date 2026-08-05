package com.promptstudio.relay.ws;

import com.promptstudio.relay.controller.RelayWebMapper;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.service.RelayFeedbackFailed;
import com.promptstudio.relay.service.RelayFeedbackReady;
import com.promptstudio.relay.service.RelayGradingFinished;
import com.promptstudio.relay.service.RelayGradingStarted;
import com.promptstudio.relay.service.RelayRoomChanged;
import com.promptstudio.relay.service.RelayTurnFailed;
import com.promptstudio.relay.service.RelayTurnFinished;
import com.promptstudio.relay.service.RelayTurnSkipped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 방 접속자 전원에게 이벤트를 밀어준다.
 *
 * <p>스프링이 관리하는 ObjectMapper를 주입받는다(부트 4의 Jackson 3). 직접 만들면 REST 응답과
 * 직렬화 설정이 갈려 같은 DTO가 소켓에서 다른 JSON으로 나간다 — Instant 필드가 ISO-8601이 아니라
 * 숫자 타임스탬프로 바뀌는 식이다. 프런트는 두 경로에서 같은 모양을 기대한다.
 */
@Component
public class RelayWebSocketBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RelayWebSocketBroadcaster.class);

    private final RelaySessionRegistry sessionRegistry;
    private final RelayWebMapper relayWebMapper;
    private final ObjectMapper objectMapper;

    public RelayWebSocketBroadcaster(
            RelaySessionRegistry sessionRegistry,
            RelayWebMapper relayWebMapper,
            ObjectMapper objectMapper
    ) {
        this.sessionRegistry = sessionRegistry;
        this.relayWebMapper = relayWebMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 커밋 이후에만 보낸다. 트랜잭션 안에서 보내면 롤백된 상태를 접속자들이 이미 화면에 그린 뒤가 된다.
     *
     * <p>{@code fallbackExecution = true}는 트랜잭션 없이 발행된 이벤트도 받게 한다 — 없으면
     * 트랜잭션 밖에서 부르는 경로가 생겼을 때 이벤트가 조용히 버려진다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoomChanged(RelayRoomChanged event) {
        broadcast(event.room().id(), roomStateEvent(event.room()));
    }

    /**
     * turn.finished를 먼저, room.state를 다음에 보낸다. 순서를 뒤집으면 클라이언트가 "다음 차례"
     * 화면을 그린 뒤에 지난 턴의 요약이 도착해 어색해진다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTurnFinished(RelayTurnFinished event) {
        Long roomId = event.room().id();

        broadcast(roomId, RelayEvent.turnFinished(relayWebMapper.toTurnSummaryResponse(event.summary())));
        broadcast(roomId, roomStateEvent(event.room()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTurnFailed(RelayTurnFailed event) {
        Long roomId = event.room().id();

        broadcast(roomId, RelayEvent.turnFailed(new TurnFailedPayload(event.turnIndex(), event.authorUserId())));
        broadcast(roomId, roomStateEvent(event.room()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTurnSkipped(RelayTurnSkipped event) {
        Long roomId = event.room().id();

        broadcast(roomId, RelayEvent.turnSkipped(
                new TurnSkippedPayload(event.turnIndex(), event.authorUserId())));
        broadcast(roomId, roomStateEvent(event.room()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGradingStarted(RelayGradingStarted event) {
        Long roomId = event.room().id();

        broadcast(roomId, RelayEvent.gradingStarted(new GradingStartedPayload(event.turnIndex())));
        broadcast(roomId, roomStateEvent(event.room()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGradingFinished(RelayGradingFinished event) {
        Long roomId = event.room().id();

        broadcast(roomId, RelayEvent.gradingFinished(relayWebMapper.toGradingOutcomeResponse(event.outcome())));
        broadcast(roomId, roomStateEvent(event.room()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFeedbackReady(RelayFeedbackReady event) {
        Long roomId = event.room().id();

        broadcast(roomId, RelayEvent.feedbackReady(relayWebMapper.toRoomResponse(event.room())));
        broadcast(roomId, roomStateEvent(event.room()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFeedbackFailed(RelayFeedbackFailed event) {
        broadcast(event.room().id(), RelayEvent.feedbackFailed(relayWebMapper.toRoomResponse(event.room())));
    }

    record TurnFailedPayload(int turnIndex, Long authorUserId) {
    }

    record TurnSkippedPayload(int turnIndex, Long authorUserId) {
    }

    record GradingStartedPayload(int turnIndex) {
    }

    /**
     * 뷰를 와이어 형식으로 바꾸는 곳은 여기 하나다. 접속 직후 스냅샷과 브로드캐스트가 각자 만들면
     * 같은 이벤트 타입이 서로 다른 모양으로 나간다(뷰의 id 대 응답의 roomId).
     */
    public void sendRoomState(WebSocketSession session, RelayRoomView room) {
        sendTo(session, roomStateEvent(room));
    }

    private RelayEvent roomStateEvent(RelayRoomView room) {
        return RelayEvent.roomState(relayWebMapper.toRoomResponse(room));
    }

    public void broadcast(Long roomId, RelayEvent event) {
        String payload = serialize(event);

        if (payload == null) {
            return;
        }

        TextMessage message = new TextMessage(payload);

        for (WebSocketSession session : sessionRegistry.sessionsOf(roomId)) {
            send(session, message, event.type());
        }
    }

    public void sendTo(WebSocketSession session, RelayEvent event) {
        String payload = serialize(event);

        if (payload != null) {
            send(session, new TextMessage(payload), event.type());
        }
    }

    /**
     * 한 세션의 전송 실패가 나머지 접속자에게 번지지 않게 한다. 이 채널로 나가는 것은 잃어도 되는
     * 알림이 아니라 게임 상태이지만, 끊긴 소켓은 재접속 시 스냅샷으로 복구된다 — 서버가 진실의
     * 원천이고 클라이언트가 누적해 들고 있는 값이 없기 때문에 성립하는 처리다.
     */
    private void send(WebSocketSession session, TextMessage message, String type) {
        if (!session.isOpen()) {
            return;
        }

        try {
            session.sendMessage(message);
        } catch (IOException | IllegalStateException exception) {
            log.warn("[RELAY] 이벤트 전송에 실패했습니다. sessionId={} | type={} | exceptionType={}",
                    session.getId(), type, exception.getClass().getSimpleName());
        }
    }

    private String serialize(RelayEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            log.error("[RELAY] 이벤트 직렬화에 실패했습니다. type={}", event.type(), exception);

            return null;
        }
    }
}
