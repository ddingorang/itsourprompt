package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.LlmCallUsage;

import java.util.List;

/**
 * 피드백 생성 실패. 같은 요청을 다시 제출하면 통과하는 경우가 많아 실패 유형을 {@link #reason()}으로 구분한다.
 */
public class FeedbackGenerationException extends RuntimeException implements LlmUsageCarrier {

    /**
     * 턴 수와 피드백 개수가 어긋난 응답. 구조화 출력 스키마가 개수를 강제하지 못해 호출마다 성패가 갈린다.
     */
    public static final String TURN_COUNT_MISMATCH = "turn-count-mismatch";

    /**
     * 완성 토큰 상한에서 잘린 응답. reasoning 토큰이 같은 예산을 쓰므로 턴이 길수록 자주 걸린다.
     */
    public static final String TRUNCATED = "truncated";

    /**
     * 구조화 출력인데도 JSON으로 읽히지 않는 응답.
     */
    public static final String INVALID_JSON = "invalid-json";

    /**
     * 본문이 아예 비어 온 응답.
     */
    public static final String EMPTY_CONTENT = "empty-content";

    /**
     * 턴별 피드백은 왔지만 전체 피드백이 빈 응답.
     */
    public static final String EMPTY_OVERALL = "empty-overall";

    /**
     * 근거로 내놓은 인용이 입력에 없는 응답. 모델이 없는 사실을 지어낸 것이라 그 판정을 믿을 수 없다.
     */
    public static final String QUOTE_NOT_FOUND = "quote-not-found";

    /**
     * 모델 호출 자체가 실패한 경우(rate limit, 5xx 등).
     */
    public static final String PROVIDER_ERROR = "provider-error";

    /**
     * 호출 스레드가 중단된 경우. 서버 종료 경로에서만 나온다.
     */
    public static final String INTERRUPTED = "interrupted";

    private static final String UNSPECIFIED = "unspecified";

    private final String reason;
    private final List<LlmCallUsage> llmCalls;

    public FeedbackGenerationException(String message) {
        this(UNSPECIFIED, message, null, List.of());
    }

    public FeedbackGenerationException(String message, Throwable cause) {
        this(UNSPECIFIED, message, cause, List.of());
    }

    public FeedbackGenerationException(String reason, String message, Throwable cause) {
        this(reason, message, cause, List.of());
    }

    /**
     * @param llmCalls 실패 시점까지 성공한 호출의 사용량. 파싱 실패는 이미 끝난 호출의 사용량을 함께 실어야 한다.
     */
    public FeedbackGenerationException(String reason, String message, Throwable cause, List<LlmCallUsage> llmCalls) {
        super(message, cause);
        this.reason = reason;
        this.llmCalls = List.copyOf(llmCalls);
    }

    public String reason() {
        return reason;
    }

    @Override
    public List<LlmCallUsage> llmCalls() {
        return llmCalls;
    }

    @Override
    public String errorType() {
        return reason;
    }
}
