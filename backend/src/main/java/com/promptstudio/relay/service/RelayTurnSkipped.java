package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayRoomView;

/**
 * 턴이 치지 않은 채 건너뛰어졌다(이탈한 주자의 차례이거나 입력 마감 초과).
 * 대기자들이 "왜 갑자기 다음 사람 차례지?"가 되지 않게 알린다.
 */
public record RelayTurnSkipped(RelayRoomView room, int turnIndex, Long authorUserId) {
}
