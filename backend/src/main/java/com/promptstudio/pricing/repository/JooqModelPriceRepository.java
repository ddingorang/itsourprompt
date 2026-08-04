package com.promptstudio.pricing.repository;

import com.promptstudio.pricing.LlmPricingProperties;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static com.promptstudio.pricing.repository.ModelPriceTables.CACHED_INPUT;
import static com.promptstudio.pricing.repository.ModelPriceTables.INPUT;
import static com.promptstudio.pricing.repository.ModelPriceTables.MODEL;
import static com.promptstudio.pricing.repository.ModelPriceTables.MODEL_PRICE;
import static com.promptstudio.pricing.repository.ModelPriceTables.OUTPUT;
import static com.promptstudio.pricing.repository.ModelPriceTables.UPDATED_AT;

@Repository
public class JooqModelPriceRepository implements ModelPriceRepository {

    private final DSLContext dsl;

    public JooqModelPriceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void upsertAll(Map<String, LlmPricingProperties.ModelRate> models) {
        Instant now = Instant.now();

        for (Map.Entry<String, LlmPricingProperties.ModelRate> entry : models.entrySet()) {
            LlmPricingProperties.ModelRate rate = entry.getValue();

            dsl.insertInto(MODEL_PRICE)
                    .columns(MODEL, INPUT, CACHED_INPUT, OUTPUT, UPDATED_AT)
                    .values(entry.getKey(), rate.input(), rate.cachedInput(), rate.output(), now)
                    .onConflict(MODEL)
                    .doUpdate()
                    .set(INPUT, rate.input())
                    .set(CACHED_INPUT, rate.cachedInput())
                    .set(OUTPUT, rate.output())
                    .set(UPDATED_AT, now)
                    .execute();
        }
    }
}
