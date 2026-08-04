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
 *
 * <p>프롬프트 캐시 키는 경로마다 성격이 다르다. 코드 생성은 한 어템프트가 대화를 이어가므로 어템프트별
 * 키를 받고, 피드백 두 경로는 시스템 프롬프트가 전 사용자·전 제출에서 같아 워크로드 고정 키를 쓴다.
 * 사용자 데이터는 프리픽스 뒤에 오므로 고정 키를 써도 캐시에 섞이지 않는다. 두 피드백은 시스템 프롬프트가
 * 다르므로 키도 갈라야 한다 — 같은 키를 주면 프리픽스가 어긋나 캐시가 빗나간다.
 */
@Component
public class OpenAiChatOptionsFactory {

    private static final String FEEDBACK_PROMPT_CACHE_KEY = "feedback";
    private static final String PATTERN_FEEDBACK_PROMPT_CACHE_KEY = "pattern-feedback";

    private static final String CODE_GENERATION_REASONING_EFFORT = "none";
    private static final String FEEDBACK_REASONING_EFFORT = "low";
    private static final String SCOPE_VALIDATION_REASONING_EFFORT = "none";
    private static final int CODE_GENERATION_MAX_COMPLETION_TOKENS = 32_768;
    private static final int FINALIZE_MAX_COMPLETION_TOKENS = 8_192;
    private static final int FEEDBACK_BASE_MAX_COMPLETION_TOKENS = 4_096;
    private static final int FEEDBACK_MAX_COMPLETION_TOKENS_PER_TURN = 2_048;
    private static final int FEEDBACK_MAX_COMPLETION_TOKENS_CAP = 32_768;
    private static final String PATTERN_REASONING_EFFORT = "medium";
    private static final int PATTERN_BASE_MAX_COMPLETION_TOKENS = 8_192;
    private static final int PATTERN_MAX_COMPLETION_TOKENS_PER_TURN = 2_048;
    private static final int PATTERN_MAX_COMPLETION_TOKENS_CAP = 32_768;
    private static final int SCOPE_VALIDATION_MAX_COMPLETION_TOKENS = 256;

    private final String codeModel;
    private final String feedbackModel;
    private final String scopeModel;

    public OpenAiChatOptionsFactory(
            @Value("${OPENAI_CODE_MODEL:gpt-5.6-luna}") String codeModel,
            @Value("${OPENAI_FEEDBACK_MODEL:gpt-5.4-mini}") String feedbackModel,
            @Value("${OPENAI_SCOPE_MODEL:gpt-5.4-mini}") String scopeModel
    ) {
        this.codeModel = codeModel;
        this.feedbackModel = feedbackModel;
        this.scopeModel = scopeModel;
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
                .promptCacheKey(FEEDBACK_PROMPT_CACHE_KEY)
                .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                        .jsonSchema(FeedbackSchema.jsonSchema(turnCount))
                        .build())
                .build();
    }

    /**
     * pattern 피드백 호출 옵션. 모델은 피드백과 같지만 effort는 한 단계 위다.
     *
     * <p>이 렌즈가 하는 일은 턴마다 (그 턴의 변경 파일 ↔ 다음 턴 프롬프트)를 이름으로 대조하는
     * 것이라 판정이 프롬프트 렌즈보다 무겁다. low로 재 봤더니 reasoning을 480~540 토큰밖에 쓰지
     * 않았고, 같은 세션을 두 번 돌렸을 때 턴 2 판정이 뒤집히고 없는 사실을 지어냈다.
     *
     * <p>출력 문장 자체는 프롬프트 렌즈의 절반 이하지만 <b>예산을 낮추면 안 된다</b>. reasoning이 같은
     * 완성 토큰 예산에서 나가는데, medium에서 6턴 호출이 reasoning에만 4,979~13,879 토큰을 쓴 실측이
     * {@link #feedbackMaxCompletionTokens} 주석에 남아 있다. 잘림은 재시도 대상이 아니고
     * pattern은 프롬프트 렌즈와 함께 필수라, 예산이 모자라면 제출이 통째로 실패한다.
     * 그래서 프롬프트 렌즈보다 오히려 넉넉하게 잡는다 — 6턴이면 20,480이다.
     */
    public OpenAiChatOptions forPatternFeedback(int turnCount) {
        return OpenAiChatOptions.builder()
                .model(feedbackModel)
                .reasoningEffort(PATTERN_REASONING_EFFORT)
                .maxCompletionTokens(patternMaxCompletionTokens(turnCount))
                .promptCacheKey(PATTERN_FEEDBACK_PROMPT_CACHE_KEY)
                .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                        .jsonSchema(PatternSchema.jsonSchema(turnCount))
                        .build())
                .build();
    }

    public OpenAiChatOptions forScopeValidation() {
        return OpenAiChatOptions.builder()
                .model(scopeModel)
                .reasoningEffort(SCOPE_VALIDATION_REASONING_EFFORT)
                .maxCompletionTokens(SCOPE_VALIDATION_MAX_COMPLETION_TOKENS)
                .build();
    }

    private int feedbackMaxCompletionTokens(int turnCount) {
        return Math.min(
                FEEDBACK_BASE_MAX_COMPLETION_TOKENS + FEEDBACK_MAX_COMPLETION_TOKENS_PER_TURN * turnCount,
                FEEDBACK_MAX_COMPLETION_TOKENS_CAP
        );
    }

    private int patternMaxCompletionTokens(int turnCount) {
        return Math.min(
                PATTERN_BASE_MAX_COMPLETION_TOKENS + PATTERN_MAX_COMPLETION_TOKENS_PER_TURN * turnCount,
                PATTERN_MAX_COMPLETION_TOKENS_CAP
        );
    }
}
