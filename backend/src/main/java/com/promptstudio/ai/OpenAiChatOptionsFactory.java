package com.promptstudio.ai;

import org.springframework.ai.openai.OpenAiChatModel;
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
    private static final String FEEDBACK_REASONING_EFFORT = "low";
    private static final int CODE_GENERATION_MAX_COMPLETION_TOKENS = 32_768;
    private static final int FINALIZE_MAX_COMPLETION_TOKENS = 8_192;
    private static final int FEEDBACK_BASE_MAX_COMPLETION_TOKENS = 4_096;
    private static final int FEEDBACK_MAX_COMPLETION_TOKENS_PER_TURN = 2_048;
    private static final int FEEDBACK_MAX_COMPLETION_TOKENS_CAP = 32_768;

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

    /**
     * 턴마다 요약 두 문장과 프롬프트 정리하기·결과와 비교하기·다음 프롬프트 쓰기 세 절이 나오므로 완성 토큰을
     * 턴 수에 비례해 잡고 상한을 둔다.
     *
     * <p>이 계산은 출력 토큰만 센다. reasoning 토큰도 같은 완성 토큰 예산에서 나가므로 두 값은 묶여 있다.
     * medium에서 6턴 호출이 reasoning에만 4,979~13,879 토큰을 써 3턴 예산 10,240에 여유 76토큰까지
     * 좁혀진 적이 있다. low에서는 reasoning이 57~455라 이 계산으로 충분하다 —
     * {@link #FEEDBACK_REASONING_EFFORT}를 올리려면 이 상한부터 다시 재야 한다.
     *
     * <p>구조화 출력 스키마도 턴 수를 알아야 만들 수 있어 여기서 함께 조립한다.
     */
    public OpenAiChatOptions forFeedback(int turnCount) {
        return OpenAiChatOptions.builder()
                .model(feedbackModel)
                .reasoningEffort(FEEDBACK_REASONING_EFFORT)
                .maxCompletionTokens(feedbackMaxCompletionTokens(turnCount))
                .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                        .jsonSchema(FeedbackSchema.jsonSchema(turnCount))
                        .build())
                .build();
    }

    private int feedbackMaxCompletionTokens(int turnCount) {
        return Math.min(
                FEEDBACK_BASE_MAX_COMPLETION_TOKENS + FEEDBACK_MAX_COMPLETION_TOKENS_PER_TURN * turnCount,
                FEEDBACK_MAX_COMPLETION_TOKENS_CAP
        );
    }
}
