package com.promptstudio.attempt.domain;

import java.math.BigDecimal;

/**
 * 턴 하나가 쓴 사용량 합계. 원본 호출 행에서 파생하며 저장하지 않는다.
 *
 * @param cost   단가를 모르는 모델이 섞이면 그만큼 빠진 합계다
 * @param rounds 그 턴의 LLM 호출 횟수
 */
public record LlmUsageSummary(
        Long inputTokens,
        Long outputTokens,
        Long cachedInputTokens,
        Long reasoningTokens,
        BigDecimal cost,
        String model,
        int rounds
) {
}
