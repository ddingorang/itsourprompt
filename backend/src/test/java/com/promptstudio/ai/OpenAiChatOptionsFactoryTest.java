package com.promptstudio.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatOptionsFactoryTest {

    private final OpenAiChatOptionsFactory factory = new OpenAiChatOptionsFactory("code-model", "feedback-model");

    @Test
    void 코드_생성_옵션은_maxTokens_대신_maxCompletionTokens를_쓴다() {
        OpenAiChatOptions options = factory.forCodeGeneration("attempt-7");

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
    void 코드_생성_옵션은_구조화_출력과_프롬프트_캐시_키를_싣는다() {
        OpenAiChatOptions options = factory.forCodeGeneration("attempt-7");

        assertThat(options.getModel()).isEqualTo("code-model");
        assertThat(options.getReasoningEffort()).isEqualTo("low");
        assertThat(options.getPromptCacheKey()).isEqualTo("attempt-7");

        OpenAiChatModel.ResponseFormat responseFormat = options.getResponseFormat();
        assertThat(responseFormat).isNotNull();
        assertThat(responseFormat.getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
        assertThat(responseFormat.getJsonSchema()).isEqualTo(CodeGenerationSchema.JSON);
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
