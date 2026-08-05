package com.promptstudio.pricing;

import com.promptstudio.pricing.repository.ModelPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 부팅 때 yml의 단가표를 model_price에 한 번 복사한다.
 *
 * <p>랭킹은 저장된 attempt_llm_call.cost가 아니라 현재 단가로 전원을 다시 재므로 단가가 SQL 안에
 * 있어야 한다. 단가는 사람이 yml을 고칠 때만 바뀌므로 주기 반복이 무의미하고, 그래서
 * {@code @Scheduled}가 아니라 {@link ApplicationRunner}다.
 */
@Component
public class ModelPriceSynchronizer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelPriceSynchronizer.class);

    private final LlmPricingProperties pricing;
    private final ModelPriceRepository modelPriceRepository;

    public ModelPriceSynchronizer(LlmPricingProperties pricing, ModelPriceRepository modelPriceRepository) {
        this.pricing = pricing;
        this.modelPriceRepository = modelPriceRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        sync();
    }

    /**
     * 부팅과 별개로 다시 부를 수 있게 열어 둔다. 테스트가 단가를 지웠다가 되살릴 길이 없으면
     * "model_price는 비우면 안 된다"는 규칙이 주석 하나에만 기대게 된다.
     */
    public void sync() {
        Map<String, LlmPricingProperties.ModelRate> priced = pricing.models().entrySet().stream()
                .filter(entry -> isPriced(entry.getKey(), entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        modelPriceRepository.upsertAll(priced);

        log.info("모델 단가를 동기화했습니다 | models={}", priced.size());
    }

    /**
     * 입력이나 출력 단가가 비면 비용을 계산할 수 없다. LlmCostCalculator가 같은 조건에서 비용을
     * NULL로 남기므로, 여기서도 0으로 채우지 않고 아예 싣지 않는다.
     */
    private boolean isPriced(String model, LlmPricingProperties.ModelRate rate) {
        if (rate.input() == null || rate.output() == null) {
            log.warn("[LLM COST] 입력·출력 단가가 비어 있어 동기화하지 않습니다 | model={}", model);

            return false;
        }

        return true;
    }
}
