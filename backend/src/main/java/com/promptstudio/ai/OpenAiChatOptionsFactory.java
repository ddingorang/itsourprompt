package com.promptstudio.ai;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 워크로드별 호출 옵션을 만든다.
 *
 * <p>두 모델 모두 reasoning 모델이라 max_tokens를 거부하므로 어느 경로에서도
 * maxTokens를 세팅하지 않는다.
 */
@Component
public class OpenAiChatOptionsFactory {

    private static final String CODE_GENERATION_REASONING_EFFORT = "low";
    private static final String FEEDBACK_REASONING_EFFORT = "medium";
    private static final int CODE_GENERATION_MAX_COMPLETION_TOKENS = 32_768;
    private static final int FEEDBACK_MAX_COMPLETION_TOKENS = 8_192;

    private final String codeModel;
    private final String feedbackModel;

    public OpenAiChatOptionsFactory(
            @Value("${OPENAI_CODE_MODEL:gpt-5.6-luna}") String codeModel,
            @Value("${OPENAI_FEEDBACK_MODEL:gpt-5.4-mini}") String feedbackModel
    ) {
        this.codeModel = codeModel;
        this.feedbackModel = feedbackModel;
    }

    public OpenAiChatOptions forCodeGeneration(String promptCacheKey) {
        return OpenAiChatOptions.builder()
                .model(codeModel)
                .reasoningEffort(CODE_GENERATION_REASONING_EFFORT)
                .maxCompletionTokens(CODE_GENERATION_MAX_COMPLETION_TOKENS)
                .promptCacheKey(promptCacheKey)
                .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                        .jsonSchema(CodeGenerationSchema.JSON)
                        .build())
                .build();
    }

    public OpenAiChatOptions forFeedback() {
        return OpenAiChatOptions.builder()
                .model(feedbackModel)
                .reasoningEffort(FEEDBACK_REASONING_EFFORT)
                .maxCompletionTokens(FEEDBACK_MAX_COMPLETION_TOKENS)
                .build();
    }
}
