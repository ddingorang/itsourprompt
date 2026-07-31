package com.promptstudio.attempt.domain;

import java.util.List;

/**
 * 제출 시 배정하는 피드백. 턴별 피드백은 턴과 1:1로 순서가 맞아야 한다.
 *
 * @param turnFeedbacks 턴 순서대로의 피드백 (Markdown)
 * @param overall       세션 전체 피드백 (Markdown)
 */
public record AttemptFeedback(List<String> turnFeedbacks, String overall) {

    public AttemptFeedback {
        turnFeedbacks = List.copyOf(turnFeedbacks);
    }
}
