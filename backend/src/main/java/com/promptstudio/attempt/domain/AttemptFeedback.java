package com.promptstudio.attempt.domain;

import java.util.List;

/**
 * 제출 시 배정하는 피드백. 턴별 피드백은 턴과 1:1로 순서가 맞아야 한다.
 *
 * <p>같은 세션을 두 렌즈로 본 결과를 나란히 담는다. 프롬프트 렌즈는 프롬프트가 무엇을 전달했는지 짚고,
 * pattern 렌즈는 작업 방식에 이름을 붙인다. 두 렌즈를 중첩 타입으로 묶지 않고 평평하게 두는 이유는
 * 세 번째 렌즈를 미리 상정하지 않기 때문이다.
 *
 * @param turnFeedbacks        턴 순서대로의 프롬프트 피드백 (Markdown)
 * @param overall              세션 전체 프롬프트 피드백 (Markdown)
 * @param patternTurnFeedbacks 턴 순서대로의 pattern 피드백 (Markdown)
 * @param patternOverall       세션 전체 pattern 피드백 (Markdown)
 * @param llmCalls             프롬프트 피드백을 만드는 데 든 LLM 호출들의 사용량
 * @param patternLlmCalls      pattern 피드백을 만드는 데 든 LLM 호출들의 사용량
 */
public record AttemptFeedback(
        List<String> turnFeedbacks,
        String overall,
        List<String> patternTurnFeedbacks,
        String patternOverall,
        List<LlmCallUsage> llmCalls,
        List<LlmCallUsage> patternLlmCalls
) {

    public AttemptFeedback {
        turnFeedbacks = List.copyOf(turnFeedbacks);
        patternTurnFeedbacks = List.copyOf(patternTurnFeedbacks);
        llmCalls = List.copyOf(llmCalls);
        patternLlmCalls = List.copyOf(patternLlmCalls);
    }
}
