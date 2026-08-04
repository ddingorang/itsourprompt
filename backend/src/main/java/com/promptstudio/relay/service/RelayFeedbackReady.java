package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayRoomView;

/** 피드백까지 준비 완료. 전원이 피드백 페이지로 이동한다. */
public record RelayFeedbackReady(RelayRoomView room) {
}
