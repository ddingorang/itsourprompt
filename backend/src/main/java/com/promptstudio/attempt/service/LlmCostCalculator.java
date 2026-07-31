package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.LlmCallUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 호출 1건의 비용을 쓰기 시점 단가로 계산한다. 저장된 비용은 나중에 단가가 바뀌어도 다시 계산하지 않는다.
 */
@Component
public class LlmCostCalculator {

    private static final BigDecimal TOKENS_PER_PRICE_UNIT = new BigDecimal("1000000");
    private static final int COST_SCALE = 8;
    private static final Logger log = LoggerFactory.getLogger(LlmCostCalculator.class);

    private final LlmPricingProperties pricing;

    public LlmCostCalculator(LlmPricingProperties pricing) {
        this.pricing = pricing;
    }

    /**
     * 토큰이나 단가를 모르면 null이다 — 모르는 비용을 0으로 적으면 합계가 거짓말이 된다.
     */
    public BigDecimal costOf(LlmCallUsage usage) {
        if (usage.inputTokens() == null || usage.outputTokens() == null) {
            return null;
        }

        LlmPricingProperties.ModelRate rate = pricing.models().get(usage.model());

        if (rate == null || rate.input() == null || rate.output() == null) {
            log.warn("[LLM COST] 단가가 등록되지 않은 모델입니다 | model={}", usage.model());

            return null;
        }

        long cachedInput = usage.cachedInputTokens() == null ? 0L : usage.cachedInputTokens();
        long uncachedInput = usage.inputTokens() - cachedInput;
        BigDecimal cachedRate = rate.cachedInput() == null ? rate.input() : rate.cachedInput();

        BigDecimal total = rate.input().multiply(BigDecimal.valueOf(uncachedInput))
                .add(cachedRate.multiply(BigDecimal.valueOf(cachedInput)))
                .add(rate.output().multiply(BigDecimal.valueOf(usage.outputTokens())));

        return total.divide(TOKENS_PER_PRICE_UNIT, COST_SCALE, RoundingMode.HALF_UP);
    }
}
