package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayRoomView;

/**
 * 릴레이 턴 하나가 완료됐다. 브로드캐스터가 turn.finished와 room.state를 이 순서로 내보낸다.
 *
 * <p>스냅샷을 함께 싣는 이유는 {@link RelayRoomChanged}와 같다 — 수신 측이 다시 조회하면
 * 그 사이 다음 변경이 끼어들어 이벤트 순서와 내용이 어긋날 수 있다.
 */
public record RelayTurnFinished(RelayRoomView room, RelayTurnSummary summary) {
}
