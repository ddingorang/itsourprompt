package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayRoomView;

/** 턴 전송 요청에 대한 결과. 주자 본인에게는 REST 응답으로, 나머지에게는 소켓으로 같은 내용이 간다. */
public record RelayTurnResult(RelayRoomView room, RelayTurnSummary summary) {
}
