package com.promptstudio.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatOptionsFactoryTest {

    private final OpenAiChatOptionsFactory factory = new OpenAiChatOptionsFactory("code-model", "feedback-model");

    private final List<ToolCallback> toolCallbacks = new CodeGenerationTools(List.of()).callbacks();

    @Test
    void 코드_생성_옵션은_maxTokens_대신_maxCompletionTokens를_쓴다() {
        OpenAiChatOptions options = factory.forCodeGeneration("attempt-7", toolCallbacks);

        assertThat(options.getMaxTokens()).isNull();
        assertThat(options.getMaxCompletionTokens()).isPositive();
    }

    @Test
    void 피드백_옵션은_maxTokens_대신_maxCompletionTokens를_쓴다() {
        OpenAiChatOptions options = factory.forFeedback();

        assertThat(options.getMaxTokens()).isNull();
        assertThat(options.getMaxCompletionTokens()).isPositive();
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
    void 피드백_옵션은_구조화_출력과_프롬프트_캐시_키를_싣지_않는다() {
        OpenAiChatOptions options = factory.forFeedback();

        assertThat(options.getModel()).isEqualTo("feedback-model");
        assertThat(options.getReasoningEffort()).isEqualTo("medium");
        assertThat(options.getResponseFormat()).isNull();
        assertThat(options.getPromptCacheKey()).isNull();
    }
}
