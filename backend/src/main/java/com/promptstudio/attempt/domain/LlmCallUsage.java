package com.promptstudio.attempt.domain;

/**
 * LLM API 호출 1건이 쓴 토큰. 제공자가 준 값을 그대로 담고 비용은 저장 직전에 파생한다.
 *
 * <p>제공자가 항목을 주지 않을 수 있어 토큰은 모두 null을 허용한다 — 0과 "모름"은 다르다.
 *
 * @param seq 요청 하나 안에서의 호출 순번(1부터)
 */
public record LlmCallUsage(
        int seq,
        String model,
        Long inputTokens,
        Long outputTokens,
        Long cachedInputTokens,
        Long reasoningTokens,
        long latencyMs
) {
}
