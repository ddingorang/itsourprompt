package com.promptstudio.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 모델별 단가표. 값은 100만 토큰당 USD다.
 *
 * <p>모델명에 점이 들어가므로 yml 키는 대괄호로 감싸야 한다(예: {@code "[gpt-5.6-luna]"}).
 */
@ConfigurationProperties(prefix = "llm.pricing")
public record LlmPricingProperties(Map<String, ModelRate> models) {

    /** 단가의 기준 수량. 표의 값은 이 토큰 수당 USD다. */
    public static final int TOKENS_PER_PRICE_UNIT = 1_000_000;

    /**
     * 비용을 보관하는 소수 자릿수.
     *
     * <p>비용을 내는 경로가 둘이다 — 호출을 저장할 때(LlmCostCalculator)와 랭킹이 현재 단가로 다시 잴
     * 때(JooqRankingQueryRepository). 자릿수가 갈리면 같은 어템프트의 화면 총계와 랭킹 값이 어긋나므로
     * 주석이 아니라 이 상수가 둘을 묶는다.
     */
    public static final int COST_SCALE = 8;

    public LlmPricingProperties {
        models = models == null ? Map.of() : Map.copyOf(models);
    }

    /**
     * @param cachedInput 캐시 적중 입력 토큰 단가. 없으면 input 단가를 적용한다
     */
    public record ModelRate(BigDecimal input, BigDecimal cachedInput, BigDecimal output) {
    }
}
