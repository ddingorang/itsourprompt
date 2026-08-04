package com.promptstudio.pricing;

import com.promptstudio.support.DatabaseTest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * 랭킹이 현재 단가로 전원을 다시 재려면 yml의 단가가 SQL에서 조인 가능해야 한다.
 * 그 사본이 언제 어떻게 갱신되는지를 확인한다.
 */
class ModelPriceSynchronizerTest extends DatabaseTest {

    @Autowired
    private ModelPriceSynchronizer modelPriceSynchronizer;

    @Autowired
    private DSLContext dsl;

    @Test
    void 부팅하면_yml의_단가가_model_price에_들어간다() {
        Record price = findPrice("test-model");

        assertThat(price).isNotNull();
        assertThat(price.get("input", BigDecimal.class)).isEqualByComparingTo("1.0");
        assertThat(price.get("cached_input", BigDecimal.class)).isEqualByComparingTo("0.5");
        assertThat(price.get("output", BigDecimal.class)).isEqualByComparingTo("2.0");
    }

    @Test
    void 다시_동기화해도_행이_늘지_않는다() {
        int before = countPrices();

        modelPriceSynchronizer.run(null);

        assertThat(countPrices()).isEqualTo(before);
    }

    @Test
    void yml에서_사라진_모델의_단가는_지우지_않는다() {
        // 지우면 그 모델로 푼 과거 어템프트가 자격 조건(비용 계산 가능)에 걸려 조용히 랭킹에서 빠진다.
        dsl.execute("INSERT INTO model_price (model, input, cached_input, output, updated_at)"
                + " VALUES ('retired-model', 9.0, 9.0, 9.0, now()) ON CONFLICT (model) DO NOTHING");

        modelPriceSynchronizer.run(null);

        assertThat(findPrice("retired-model")).isNotNull();
    }

    private Record findPrice(String model) {
        return dsl.fetchOne("SELECT input, cached_input, output FROM model_price WHERE model = ?", model);
    }

    private int countPrices() {
        return dsl.fetchCount(table(name("model_price")));
    }
}
