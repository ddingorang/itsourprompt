package com.promptstudio.relay.ws;

import com.promptstudio.global.security.AppUserDetails;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.exception.RelayRoomNotFoundException;
import com.promptstudio.relay.service.RelayRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code /ws/relay/{roomId}} 핸들러. 게임 상태를 밀어주는 서버 권위 채널이자
 * WebRTC 시그널링(offer/answer/ICE)의 중계다.
 *
 * <p>참가자만 붙을 수 있다. 방을 구경하는 상태는 없고, 입장한 사람은 곧 주자다. 입장 전에 방을
 * 살펴보는 것은 REST 조회가 맡는다.
 *
 * <p>게임 명령은 이 소켓으로 받지 않는다. 방 개설·입장·턴 전송은 전부 REST다 — 인증·검증·에러
 * 응답과 멱등 처리를 이미 갖춘 경로를 재발명하지 않기 위해서다. 클라이언트가 이 소켓으로 보내는
 * 것은 시그널링뿐이고, 서버는 payload(SDP·ICE 후보)를 해석하지 않는다. 타이핑 미리보기와
 * 리액션은 여기도 지나지 않는다 — 피어끼리 DataChannel로 직접 주고받는다.
 */
@Component
public class RelayWebSocketHandler extends TextWebSocketHandler {

    static final String ATTRIBUTE_ROOM_ID = "relayRoomId";
    static final String ATTRIBUTE_USER_ID = "relayUserId";

    /**
     * 한 세션에 대기시킬 수 있는 미전송 바이트. 느린 클라이언트 하나가 힙을 먹지 않게 막는다 —
     * 넘기면 세션이 닫히고, 재접속하면 스냅샷으로 복구된다.
     */
    private static final int SEND_BUFFER_LIMIT = 512 * 1024;

    private static final long SEND_TIME_LIMIT_MILLIS = 10_000;

    /** 재접속(새로고침)에 밀려난 옛 세션에 보내는 종료 사유. */
    private static final CloseStatus SESSION_REPLACED = CloseStatus.NORMAL.withReason("session-replaced");

    private static final Set<String> SIGNAL_TYPES =
            Set.of(RelayEvent.SIGNAL_OFFER, RelayEvent.SIGNAL_ANSWER, RelayEvent.SIGNAL_ICE);

    private static final Logger log = LoggerFactory.getLogger(RelayWebSocketHandler.class);

    private final RelayRoomService relayRoomService;
    private final RelaySessionRegistry sessionRegistry;
    private final RelayWebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public RelayWebSocketHandler(
            RelayRoomService relayRoomService,
            RelaySessionRegistry sessionRegistry,
            RelayWebSocketBroadcaster broadcaster,
            ObjectMapper objectMapper
    ) {
        this.relayRoomService = relayRoomService;
        this.sessionRegistry = sessionRegistry;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession rawSession) throws Exception {
        Long userId = userIdOf(rawSession);

        if (userId == null) {
            rawSession.close(CloseStatus.POLICY_VIOLATION.withReason("unauthenticated"));

            return;
        }

        Long roomId = roomIdOf(rawSession);

        if (roomId == null) {
            rawSession.close(CloseStatus.BAD_DATA.withReason("invalid room id"));

            return;
        }

        // 브로드캐스트는 요청 스레드·리스너 스레드·스케줄러에서 동시에 올 수 있고,
        // WebSocketSession의 sendMessage는 동시 호출에 안전하지 않다.
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(
                rawSession, (int) SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_LIMIT);

        session.getAttributes().put(ATTRIBUTE_ROOM_ID, roomId);
        session.getAttributes().put(ATTRIBUTE_USER_ID, userId);

        // 등록 전에 방 존재를 확인한다. 없는 방에 붙은 세션은 아무 이벤트도 받지 못하고 남는다.
        RelayRoomView room;

        try {
            room = relayRoomService.getRoom(roomId);
        } catch (RelayRoomNotFoundException exception) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("room not found"));

            return;
        }

        // 방을 구경하는 상태는 없다 — 입장한 사람만 이 채널을 받는다.
        // 스냅샷에 참가자 목록이 들어 있으므로 조회를 한 번 더 하지 않는다.
        if (!isParticipant(room, userId)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("not a participant"));

            return;
        }

        broadcaster.sendRoomState(session, room);

        // 등록 후에 peer.list를 만들면 자신이 목록에 섞인다 — 등록 전에 먼저 읽는다.
        List<Long> peers = new ArrayList<>(sessionRegistry.userIdsOf(roomId));
        peers.remove(userId);

        WebSocketSession replaced = sessionRegistry.add(roomId, userId, session);

        // 같은 사용자의 옛 소켓(다른 탭·죽은 연결)은 밀어낸다. mesh가 userId 단위라 소켓이
        // 둘이면 시그널이 어느 쪽으로 갈지 정의되지 않는다.
        if (replaced != null) {
            closeQuietly(replaced);
        }

        // 새로 온 쪽이 offer를 만든다는 규약. peer.list를 받은 사람이 initiator다.
        broadcaster.sendTo(session, new RelayEvent(RelayEvent.PEER_LIST, new PeerListPayload(peers)));

        // 밀려난 재접속에는 joined를 다시 알리지 않는다 — 기존 피어들은 그 사용자와의 연결을
        // 유지하고 있다고 믿고 있고, ICE 재협상은 offer가 다시 오면 그때 일어난다.
        if (replaced == null) {
            broadcastToOthers(roomId, userId, new RelayEvent(RelayEvent.PEER_JOINED, new PeerPayload(userId)));
        }

        log.info("[RELAY] socket connected | roomId={} | userId={} | sessionId={} | replaced={}",
                roomId, userId, session.getId(), replaced != null);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = roomIdOf(session);
        Long userId = userIdOf(session);

        if (roomId == null || userId == null) {
            return;
        }

        // 밀려난 옛 세션의 종료다 — 새 세션이 이미 그 자리에 있으므로 지우지도 알리지도 않는다.
        if (!removeRegistered(roomId, userId, session)) {
            log.info("[RELAY] replaced socket closed | roomId={} | userId={} | sessionId={}",
                    roomId, userId, session.getId());

            return;
        }

        broadcastToOthers(roomId, userId, new RelayEvent(RelayEvent.PEER_LEFT, new PeerPayload(userId)));

        log.info("[RELAY] socket closed | roomId={} | userId={} | sessionId={} | code={}",
                roomId, userId, session.getId(), status.getCode());
    }

    /**
     * 시그널링 중계. 발신자는 항상 세션에서 확정한다 — 클라이언트가 fromUserId를 실어 보내도
     * 무시된다(위조 방지). payload는 불투명하게 전달한다.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long roomId = roomIdOf(session);
        Long userId = userIdOf(session);

        if (roomId == null || userId == null) {
            return;
        }

        SignalMessage signal = parse(message.getPayload());

        if (signal == null || signal.type() == null || !SIGNAL_TYPES.contains(signal.type())) {
            log.debug("[RELAY] 시그널이 아닌 수신 메시지를 버립니다 | roomId={} | userId={}", roomId, userId);

            return;
        }

        if (signal.targetUserId() == null) {
            sendSignalError(session, signal.type(), null, "target-missing");

            return;
        }

        WebSocketSession target = sessionRegistry.sessionOf(roomId, signal.targetUserId());

        // 대상이 접속 중이 아니면 보낸 쪽에 알린다. 조용히 버리면 발신자는 응답 없는 offer를
        // 하염없이 기다린다. 대상이 곧 재접속하면 peer.joined가 다시 오므로 그때 재시도한다.
        if (target == null) {
            sendSignalError(session, signal.type(), signal.targetUserId(), "peer-not-connected");

            return;
        }

        broadcaster.sendTo(target, new RelayEvent(signal.type(), new SignalPayload(userId, signal.payload())));
    }

    private void sendSignalError(WebSocketSession session, String type, Long targetUserId, String reason) {
        broadcaster.sendTo(session, new RelayEvent(
                RelayEvent.SIGNAL_ERROR, new SignalErrorPayload(type, targetUserId, reason)));
    }

    private SignalMessage parse(String payload) {
        try {
            return objectMapper.readValue(payload, SignalMessage.class);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private void broadcastToOthers(Long roomId, Long exceptUserId, RelayEvent event) {
        for (Long peerId : sessionRegistry.userIdsOf(roomId)) {
            if (peerId.equals(exceptUserId)) {
                continue;
            }

            WebSocketSession peer = sessionRegistry.sessionOf(roomId, peerId);

            if (peer != null) {
                broadcaster.sendTo(peer, event);
            }
        }
    }

    private boolean removeRegistered(Long roomId, Long userId, WebSocketSession session) {
        // 데코레이터로 감싼 세션이 레지스트리에 있으므로, 종료 콜백의 원본 세션과 id로 대조한다.
        WebSocketSession registered = sessionRegistry.sessionOf(roomId, userId);

        if (registered == null || !registered.getId().equals(session.getId())) {
            return false;
        }

        return sessionRegistry.remove(roomId, userId, registered);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(SESSION_REPLACED);
        } catch (Exception exception) {
            log.debug("[RELAY] 밀려난 세션을 닫지 못했습니다 | sessionId={}", session.getId());
        }
    }

    private boolean isParticipant(RelayRoomView room, Long userId) {
        for (RelayRoomView.RelayParticipantView participant : room.participants()) {
            if (participant.userId().equals(userId)) {
                // 게임 중 이탈은 최종 결정이다. REST 입장(join)이 거절하므로 정상 경로로는
                // 여기 닿지 않지만, 소켓만 직접 여는 우회도 같은 규칙으로 막는다.
                return !participant.left();
            }
        }

        return false;
    }

    /**
     * 핸드셰이크는 JSESSIONID를 실은 평범한 HTTP 요청이므로 시큐리티 필터가 인증 컨텍스트를
     * 채워 준다. 사용자 ID를 쿼리 파라미터로 받지 않는 이유는 REST와 같다 — 위조 방지.
     */
    private Long userIdOf(WebSocketSession session) {
        Object cached = session.getAttributes().get(ATTRIBUTE_USER_ID);

        if (cached instanceof Long userId) {
            return userId;
        }

        Principal principal = session.getPrincipal();

        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AppUserDetails user) {
            return user.id();
        }

        return null;
    }

    private Long roomIdOf(WebSocketSession session) {
        Object cached = session.getAttributes().get(ATTRIBUTE_ROOM_ID);

        if (cached instanceof Long roomId) {
            return roomId;
        }

        URI uri = session.getUri();

        if (uri == null) {
            return null;
        }

        String path = uri.getPath();
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);

        try {
            return Long.parseLong(lastSegment);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 클라이언트 → 서버 시그널. 모르는 필드는 무시한다(계약 진화 규칙은 rabbit 메시지와 같다). */
    record SignalMessage(String type, Long targetUserId, JsonNode payload) {
    }

    record SignalPayload(Long fromUserId, JsonNode data) {
    }

    record SignalErrorPayload(String type, Long targetUserId, String reason) {
    }

    record PeerListPayload(List<Long> userIds) {
    }

    record PeerPayload(Long userId) {
    }
}
