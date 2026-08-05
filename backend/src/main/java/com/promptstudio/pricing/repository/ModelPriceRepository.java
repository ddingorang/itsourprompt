package com.promptstudio.pricing.repository;

import com.promptstudio.pricing.LlmPricingProperties;

import java.util.Map;

public interface ModelPriceRepository {

    /**
     * 단가표를 model_price에 반영한다. 지우지 않고 덮어쓰기만 한다 — yml에서 사라진 모델의 행을
     * 지우면 그 모델로 푼 과거 어템프트가 랭킹에서 조용히 사라진다.
     */
    void upsertAll(Map<String, LlmPricingProperties.ModelRate> models);
}
