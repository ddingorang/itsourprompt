package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayRoomView;

/**
 * 턴의 코드 생성이 실패해 같은 좌석이 재시도할 수 있게 되돌렸다.
 * 대기 중인 다른 참가자들도 알아야 한다 — 안 알리면 "생성 중" 화면에 갇힌다.
 */
public record RelayTurnFailed(RelayRoomView room, int turnIndex, Long authorUserId) {
}
