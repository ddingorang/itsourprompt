package com.promptstudio.relay.service;

import java.util.List;

/**
 * 릴레이 피드백 페이지 하나 분량. 어템프트의 턴별 피드백에 "누가 그 턴을 쳤는가"와 채점 점수를
 * 이어붙인 것 — 릴레이에서 턴별 피드백은 곧 주자별 피드백이다.
 *
 * @param overall 게임 전체(어템프트 전체)에 대한 총평
 */
public record RelayFeedbackView(String overall, List<TurnFeedback> turns) {

    /**
     * @param feedback 이 턴의 프롬프트에 대한 AI 피드백
     * @param delta    직전 통과 수 대비 증가분 = 이 주자의 기여도. 기준이 없으면 null
     */
    public record TurnFeedback(
            int turnIndex,
            int seatOrder,
            int lap,
            Long authorUserId,
            String nickname,
            String feedback,
            Integer passedCount,
            Integer totalCount,
            Integer delta
    ) {
    }
}
