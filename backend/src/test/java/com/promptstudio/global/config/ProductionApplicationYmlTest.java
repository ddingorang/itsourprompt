package com.promptstudio.global.config;

import com.promptstudio.attempt.service.LlmPricingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 application.yml이 실제로 바인딩되는지 확인한다.
 *
 * <p>테스트 클래스패스의 application.yml이 운영 yml을 통째로 대체하므로(병합이 아니다) 운영 yml이
 * 어떻게 깨져도 전체 테스트가 초록불이고 배포 후 기동에서만 터진다. 그래서 클래스패스가 아니라
 * 파일 경로로 직접 읽는다.</p>
 *
 * <p>스프링 컨텍스트는 띄우지 않는다 — 운영 yml의 datasource·rabbitmq·api-key는 기본값 없는
 * 플레이스홀더라 .env 없이는 컨텍스트가 뜨지 않는다. 로더와 Binder만 직접 쓴다.</p>
 */
class ProductionApplicationYmlTest {

    private static final Resource PRODUCTION_YML = new FileSystemResource("src/main/resources/application.yml");

    @Test
    void 운영_yml은_YAML로_파싱된다() throws IOException {
        assertThat(PRODUCTION_YML.exists()).isTrue();

        assertThat(loadProductionYml()).isNotEmpty();
    }

    @Test
    void 운영_yml의_단가표가_LlmPricingProperties로_바인딩된다() throws IOException {
        assertThat(bindPricing().models()).isNotEmpty();
    }

    @Test
    void 등록된_모델은_모두_입력과_출력_단가를_가진다() throws IOException {
        // cachedInput은 null이어도 된다 — LlmCostCalculator가 입력 단가로 대체한다.
        assertThat(bindPricing().models()).allSatisfy((model, rate) -> {
            assertThat(rate.input()).as("%s의 입력 단가", model).isNotNull();
            assertThat(rate.output()).as("%s의 출력 단가", model).isNotNull();
        });
    }

    private List<PropertySource<?>> loadProductionYml() throws IOException {
        return new YamlPropertySourceLoader().load("application.yml", PRODUCTION_YML);
    }

    /**
     * 시스템 환경변수가 섞여 결과를 오염시키지 않도록 Environment 대신 yml만 담은 소스로 바인딩한다.
     *
     * <p>바인딩이 아예 안 되면 빈 단가표로 위장하지 않고 그대로 터뜨린다 — "비었다"와 "못 읽었다"는
     * 다른 사고이고, 둘을 뭉개면 실패 메시지가 원인을 가린다.</p>
     */
    private LlmPricingProperties bindPricing() throws IOException {
        Binder binder = new Binder(ConfigurationPropertySources.from(loadProductionYml()));

        return binder.bind("llm.pricing", Bindable.of(LlmPricingProperties.class)).get();
    }
}
