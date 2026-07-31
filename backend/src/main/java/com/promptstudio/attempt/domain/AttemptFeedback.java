package com.promptstudio.attempt.domain;

import java.util.List;

/**
 * 제출 시 배정하는 피드백. 턴별 피드백은 턴과 1:1로 순서가 맞아야 한다.
 *
 * @param turnFeedbacks 턴 순서대로의 피드백 (Markdown)
 * @param overall       세션 전체 피드백 (Markdown)
 * @param llmCalls      이 피드백을 만드는 데 든 LLM 호출들의 사용량
 */
public record AttemptFeedback(List<String> turnFeedbacks, String overall, List<LlmCallUsage> llmCalls) {

    public AttemptFeedback {
        turnFeedbacks = List.copyOf(turnFeedbacks);
        llmCalls = List.copyOf(llmCalls);
    }
}
