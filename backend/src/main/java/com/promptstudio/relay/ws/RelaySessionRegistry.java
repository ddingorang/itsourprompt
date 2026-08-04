package com.promptstudio.relay.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 방별 접속 세션. userId를 키로 든다 — 시그널링 중계가 "누구에게"를 userId로 지정하기 때문이다.
 *
 * <p>같은 사용자의 두 번째 접속은 첫 접속을 밀어낸다(등록이 새 세션을 반환하면 호출자가 옛
 * 세션을 닫는다). mesh가 userId 단위로 짜여 있어 한 사용자의 소켓이 둘이면 시그널이 어느 쪽으로
 * 갈지 정의되지 않고, 재접속(새로고침)이 흔한 경로라 옛 소켓이 이기면 돌아온 사람이 갇힌다.
 *
 * <p>단일 인스턴스 전제이며, 여러 인스턴스로 늘리면 Redis pub/sub이 필요하다
 * (SecurityConfig의 세션 저장소, FeedbackGenerationGuard와 같은 전제).
 */
@Component
public class RelaySessionRegistry {

    private final Map<Long, Map<Long, WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    /**
     * @return 같은 사용자가 이미 접속해 있었다면 그 옛 세션. 호출자가 닫아야 한다
     */
    public WebSocketSession add(Long roomId, Long userId, WebSocketSession session) {
        return sessionsByRoom
                .computeIfAbsent(roomId, key -> new ConcurrentHashMap<>())
                .put(userId, session);
    }

    /**
     * 등록된 세션이 이 세션일 때만 지운다. 밀려난 옛 세션의 종료 콜백이 새 세션을 지우면
     * 방금 재접속한 사람이 유령이 된다.
     *
     * @return 실제로 지웠는가 — 종료 브로드캐스트(peer.left)를 밀려난 세션이 또 내보내지 않게 한다
     */
    public boolean remove(Long roomId, Long userId, WebSocketSession session) {
        Map<Long, WebSocketSession> sessions = sessionsByRoom.get(roomId);

        if (sessions == null || !sessions.remove(userId, session)) {
            return false;
        }

        // 방의 마지막 세션이 끊기면 방 키까지 지운다 — 안 지우면 지나간 방의 빈 맵이 계속 쌓인다.
        sessionsByRoom.computeIfPresent(roomId, (key, value) -> value.isEmpty() ? null : value);

        return true;
    }

    public WebSocketSession sessionOf(Long roomId, Long userId) {
        Map<Long, WebSocketSession> sessions = sessionsByRoom.get(roomId);

        return sessions == null ? null : sessions.get(userId);
    }

    /** 접속 중인 참가자의 userId들. 새로 온 피어가 누구에게 offer를 보낼지 정하는 목록이다. */
    public List<Long> userIdsOf(Long roomId) {
        Map<Long, WebSocketSession> sessions = sessionsByRoom.get(roomId);

        return sessions == null ? List.of() : new ArrayList<>(sessions.keySet());
    }

    /**
     * 순회 중 다른 스레드가 끊어도 안전하도록 복사해 넘긴다.
     */
    public Collection<WebSocketSession> sessionsOf(Long roomId) {
        Map<Long, WebSocketSession> sessions = sessionsByRoom.get(roomId);

        return sessions == null ? List.of() : List.copyOf(sessions.values());
    }
}
