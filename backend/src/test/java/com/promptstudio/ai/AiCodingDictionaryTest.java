package com.promptstudio.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCodingDictionaryTest {

    @Test
    void 사전은_용어_11개를_한_줄씩_담는다() {
        assertThat(AiCodingDictionary.terms().lines()).hasSize(11);
    }

    @Test
    void 모든_줄이_용어와_뜻풀이를_구분자_하나로_나눈다() {
        assertThat(AiCodingDictionary.terms().lines()).allSatisfy(line -> {
            String[] parts = line.split(" \\| ", -1);

            assertThat(parts).hasSize(2);
            assertThat(parts[0]).isNotBlank();
            assertThat(parts[1]).isNotBlank();
        });
    }

    /**
     * 이름이 될 수 있는 넷은 반드시 있어야 한다 — 프롬프트가 이 넷만 이름으로 허용한다.
     */
    @Test
    void 이름으로_쓸_수_있는_넷을_담는다() {
        assertThat(AiCodingDictionary.terms())
                .contains("vibe coding | ")
                .contains("human review | ")
                .contains("human-in-the-loop | ")
                .contains("design concept | ");
    }

    /**
     * 기계 부품 이름은 이름이 될 수 없는데도 목록에 있다는 것만으로 모델을 끌어당겼다 — 실호출에서
     * primary source가 4턴 중 3턴을 차지했다. 지시로 막지 않고 목록에서 뺀다.
     */
    @Test
    void 이름이_될_수_없는_기계_부품_용어는_담지_않는다() {
        assertThat(AiCodingDictionary.terms())
                .doesNotContain("primary source | ", "secondary source | ", "context window | ",
                        "token | ", "prefix cache | ", "smart zone | ");
    }

    /**
     * 용어명은 업계 말이라 그대로 쓰고 뜻풀이만 우리 한국어로 다시 쓴다.
     */
    @Test
    void 용어명은_번역하지_않고_원어로_둔다() {
        assertThat(AiCodingDictionary.terms())
                .doesNotContain("바이브 코딩")
                .doesNotContain("휴먼 리뷰")
                .doesNotContain("컨텍스트 |");
    }
}
