package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.LlmCallUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCostCalculatorTest {

    private final LlmCostCalculator costCalculator = new LlmCostCalculator(new LlmPricingProperties(Map.of(
            "test-model", new LlmPricingProperties.ModelRate(
                    new BigDecimal("1.0"), new BigDecimal("0.5"), new BigDecimal("2.0"))
    )));

    @Test
    void 등록된_모델의_비용을_단가표로_계산한다() {
        BigDecimal cost = costCalculator.costOf(usage("test-model", 1_000L, 200L, 400L));

        // (1000 - 400) * 1.0 + 400 * 0.5 + 200 * 2.0 = 1200, 100만 토큰 기준이므로 1200 / 1_000_000
        assertThat(cost).isEqualByComparingTo("0.00120000");
        assertThat(cost.scale()).isEqualTo(8);
    }

    @Test
    void 캐시_토큰을_모르면_입력_전량을_입력_단가로_계산한다() {
        BigDecimal cost = costCalculator.costOf(usage("test-model", 1_000L, 200L, null));

        // 1000 * 1.0 + 200 * 2.0 = 1400
        assertThat(cost).isEqualByComparingTo("0.00140000");
    }

    @Test
    void 캐시_단가가_없으면_입력_단가를_적용한다() {
        LlmCostCalculator calculator = new LlmCostCalculator(new LlmPricingProperties(Map.of(
                "no-cache-rate", new LlmPricingProperties.ModelRate(
                        new BigDecimal("1.0"), null, new BigDecimal("2.0"))
        )));

        BigDecimal cost = calculator.costOf(usage("no-cache-rate", 1_000L, 200L, 400L));

        // 캐시 400도 입력 단가로 친다: 1000 * 1.0 + 200 * 2.0 = 1400
        assertThat(cost).isEqualByComparingTo("0.00140000");
    }

    @Test
    void 단가가_등록되지_않은_모델은_비용을_남기지_않는다() {
        assertThat(costCalculator.costOf(usage("unknown-model", 1_000L, 200L, 0L))).isNull();
    }

    @Test
    void 토큰을_모르면_비용을_남기지_않는다() {
        assertThat(costCalculator.costOf(usage("test-model", null, 200L, 0L))).isNull();
        assertThat(costCalculator.costOf(usage("test-model", 1_000L, null, 0L))).isNull();
    }

    private LlmCallUsage usage(String model, Long inputTokens, Long outputTokens, Long cachedInputTokens) {
        return new LlmCallUsage(1, model, inputTokens, outputTokens, cachedInputTokens, null, 100L);
    }
}
