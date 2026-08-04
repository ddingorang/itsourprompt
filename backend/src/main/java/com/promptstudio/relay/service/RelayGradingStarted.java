package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayRoomView;

import java.util.UUID;

/** 이번 턴의 자동 채점이 큐에 들어갔다. 대기자 화면이 "채점 중"으로 넘어간다. */
public record RelayGradingStarted(RelayRoomView room, int turnIndex, UUID runId) {
}
