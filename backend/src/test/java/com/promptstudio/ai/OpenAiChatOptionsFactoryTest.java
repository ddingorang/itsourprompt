package com.promptstudio.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatOptionsFactoryTest {

    private final OpenAiChatOptionsFactory factory = new OpenAiChatOptionsFactory(
            "code-model", "feedback-model", "scope-model");

    private final List<ToolCallback> toolCallbacks = new CodeGenerationTools(List.of()).callbacks();

    @Test
    void 코드_생성_옵션은_maxTokens_대신_maxCompletionTokens를_쓴다() {
        OpenAiChatOptions options = factory.forCodeGeneration("attempt-7", toolCallbacks);

        assertThat(options.getMaxTokens()).isNull();
        assertThat(options.getMaxCompletionTokens()).isPositive();
    }

    @Test
    void 피드백_옵션은_maxTokens_대신_maxCompletionTokens를_쓴다() {
        OpenAiChatOptions options = factory.forFeedback(1);

        assertThat(options.getMaxTokens()).isNull();
        assertThat(options.getMaxCompletionTokens()).isPositive();
    }

    @Test
    void 범위_판독_옵션은_작은_출력_예산과_전용_모델을_쓴다() {
        OpenAiChatOptions options = factory.forScopeValidation();

        assertThat(options.getModel()).isEqualTo("scope-model");
        assertThat(options.getReasoningEffort()).isEqualTo("none");
        assertThat(options.getMaxCompletionTokens()).isEqualTo(256);
    }

    /**
     * 턴별 피드백이라 출력 길이가 턴 수에 따라 늘어난다.
     */
    @Test
    void 피드백_옵션의_완성_토큰은_턴_수에_비례한다() {
        assertThat(factory.forFeedback(1).getMaxCompletionTokens()).isEqualTo(6_144);
        assertThat(factory.forFeedback(3).getMaxCompletionTokens()).isEqualTo(10_240);
    }

    @Test
    void 피드백_옵션의_완성_토큰에는_상한이_있다() {
        assertThat(factory.forFeedback(100).getMaxCompletionTokens()).isEqualTo(32_768);
    }

    @Test
    void 코드_생성_옵션은_툴_콜백과_프롬프트_캐시_키를_싣고_responseFormat이_없다() {
        OpenAiChatOptions options = factory.forCodeGeneration("attempt-7", toolCallbacks);

        assertThat(options.getModel()).isEqualTo("code-model");
        assertThat(options.getPromptCacheKey()).isEqualTo("attempt-7");
        assertThat(options.getToolCallbacks()).hasSameSizeAs(toolCallbacks);
        assertThat(options.getResponseFormat()).isNull();
    }

    /**
     * /v1/chat/completions는 함수 툴과 reasoning_effort 병용을 거부한다(400 invalid_request_error).
     */
    @Test
    void 코드_생성_옵션의_reasoningEffort는_none이다() {
        OpenAiChatOptions options = factory.forCodeGeneration("attempt-7", toolCallbacks);

        assertThat(options.getReasoningEffort()).isEqualTo("none");
    }

    @Test
    void 마무리_옵션은_툴_콜백이_비어_있다() {
        OpenAiChatOptions options = factory.forCodeGenerationFinalize("attempt-7");

        assertThat(options.getModel()).isEqualTo("code-model");
        assertThat(options.getPromptCacheKey()).isEqualTo("attempt-7");
        assertThat(options.getToolCallbacks()).isEmpty();
    }

    /**
     * 마무리 호출엔 툴이 없지만 같은 대화를 이어가므로 코드 생성 경로와 같은 값을 쓴다.
     */
    @Test
    void 마무리_옵션의_reasoningEffort는_코드_생성과_같다() {
        OpenAiChatOptions options = factory.forCodeGenerationFinalize("attempt-7");

        assertThat(options.getReasoningEffort())
                .isEqualTo("none")
                .isEqualTo(factory.forCodeGeneration("attempt-7", toolCallbacks).getReasoningEffort());
    }

    @Test
    void 마무리_옵션의_maxCompletionTokens는_코드_생성보다_작다() {
        OpenAiChatOptions options = factory.forCodeGenerationFinalize("attempt-7");

        assertThat(options.getMaxCompletionTokens())
                .isEqualTo(8_192)
                .isLessThan(factory.forCodeGeneration("attempt-7", toolCallbacks).getMaxCompletionTokens());
    }

    @Test
    void 피드백_옵션은_턴_수에_맞는_구조화_출력을_싣고_프롬프트_캐시_키는_싣지_않는다() {
        OpenAiChatOptions options = factory.forFeedback(1);

        assertThat(options.getModel()).isEqualTo("feedback-model");
        assertThat(options.getReasoningEffort()).isEqualTo("low");
        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getJsonSchema()).contains("정확히 1개");
        assertThat(options.getPromptCacheKey()).isNull();
    }

    /**
     * description만으로 개수를 요구하면 모델이 자주 어긋난 개수를 돌려준다 — 12턴 세션은 8/8 실패했다.
     * 개수를 스키마로 못 박아 응답 자체가 계약을 지키게 한다.
     */
    @Test
    void 피드백_옵션의_구조화_출력은_턴_피드백_개수를_턴_수로_고정한다() {
        assertThat(factory.forFeedback(3).getResponseFormat().getJsonSchema())
                .contains("\"minItems\": 3")
                .contains("\"maxItems\": 3");
    }
}
