package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayRoomView;

/**
 * 피드백 생성이 실패했다. 방은 FEEDBACK_GENERATING에 머물고, 참가자가 재시도 API로 다시 건다 —
 * 알리지 않으면 전원이 "생성 중" 화면에 갇힌다.
 */
public record RelayFeedbackFailed(RelayRoomView room) {
}
