package com.promptstudio.relay.service;

import java.util.List;

/**
 * 완료된 릴레이 턴 하나의 요약. REST 응답과 WebSocket 브로드캐스트가 같은 것을 내보낸다.
 *
 * <p>프롬프트 원문은 싣지 않는다 — "이전 주자의 프롬프트를 다음 주자에게 보여줄 것인가"는
 * 방 옵션으로 열어 둔 결정이라(docs/relay-game-plan.md), 기본 전파 경로에 실어 두면
 * 나중에 숨기는 옵션을 만들 수 없다.
 */
public record RelayTurnSummary(
        int turnIndex,
        int seatOrder,
        int lap,
        Long authorUserId,
        String aiSummary,
        List<String> changedPaths
) {
}
