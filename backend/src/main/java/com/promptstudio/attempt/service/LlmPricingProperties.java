package com.promptstudio.attempt.service;

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

    public LlmPricingProperties {
        models = models == null ? Map.of() : Map.copyOf(models);
    }

    /**
     * @param cachedInput 캐시 적중 입력 토큰 단가. 없으면 input 단가를 적용한다
     */
    public record ModelRate(BigDecimal input, BigDecimal cachedInput, BigDecimal output) {
    }
}
