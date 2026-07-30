package com.promptstudio.ai;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 워크로드별 호출 옵션을 만든다.
 *
 * <p>두 모델 모두 reasoning 모델이라 max_tokens를 거부하므로 어느 경로에서도
 * maxTokens를 세팅하지 않는다.
 *
 * <p>Spring AI는 /v1/chat/completions를 쓰는데, 이 엔드포인트는 함수 툴과 reasoning_effort 병용을
 * 거부한다(400 invalid_request_error, param=reasoning_effort). 그래서 툴을 싣는 코드 생성 경로는
 * reasoning_effort를 "none"으로 둔다. 툴이 없는 피드백 경로는 제약과 무관하다.
 */
@Component
public class OpenAiChatOptionsFactory {

    private static final String CODE_GENERATION_REASONING_EFFORT = "none";
    private static final String FEEDBACK_REASONING_EFFORT = "medium";
    private static final int CODE_GENERATION_MAX_COMPLETION_TOKENS = 32_768;
    private static final int FINALIZE_MAX_COMPLETION_TOKENS = 8_192;
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

    public OpenAiChatOptions forCodeGeneration(String promptCacheKey, List<ToolCallback> toolCallbacks) {
        return codeGenerationBase(promptCacheKey)
                .maxCompletionTokens(CODE_GENERATION_MAX_COMPLETION_TOKENS)
                .toolCallbacks(toolCallbacks)
                .build();
    }

    /**
     * 툴 라운드 상한에 도달했을 때 쓰는 마무리 호출 옵션. 툴 콜백을 빈 목록으로 실어 tools를 보내지 않는다.
     *
     * <p>요약 한 단락만 받으므로 코드 생성보다 적은 완성 토큰으로 충분하다.
     */
    public OpenAiChatOptions forCodeGenerationFinalize(String promptCacheKey) {
        return codeGenerationBase(promptCacheKey)
                .maxCompletionTokens(FINALIZE_MAX_COMPLETION_TOKENS)
                .toolCallbacks(List.of())
                .build();
    }

    /**
     * 같은 대화를 이어가는 코드 생성·마무리 호출이 공유하는 모델·effort·캐시 키.
     */
    private OpenAiChatOptions.Builder codeGenerationBase(String promptCacheKey) {
        return OpenAiChatOptions.builder()
                .model(codeModel)
                .reasoningEffort(CODE_GENERATION_REASONING_EFFORT)
                .promptCacheKey(promptCacheKey);
    }

    public OpenAiChatOptions forFeedback() {
        return OpenAiChatOptions.builder()
                .model(feedbackModel)
                .reasoningEffort(FEEDBACK_REASONING_EFFORT)
                .maxCompletionTokens(FEEDBACK_MAX_COMPLETION_TOKENS)
                .build();
    }
}
