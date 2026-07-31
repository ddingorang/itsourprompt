package com.promptstudio.attempt.domain;

import java.math.BigDecimal;

/**
 * 어템프트 전체가 쓴 사용량 합계. 턴에 속하지 않는 피드백 호출과 실패 flush 행까지 포함한다.
 */
public record LlmUsageTotals(
        Long inputTokens,
        Long outputTokens,
        Long cachedInputTokens,
        Long reasoningTokens,
        BigDecimal cost
) {
}
