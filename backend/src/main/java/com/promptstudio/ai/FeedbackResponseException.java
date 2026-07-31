package com.promptstudio.ai;

import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.port.FeedbackGenerationException;

import java.util.List;

/**
 * AI가 돌려준 본문이 피드백 계약을 어겼을 때의 실패. 호출 자체는 성공했으므로 프로바이더 실패와 구분한다.
 *
 * <p>같은 요청을 다시 보내면 통과하는 경우가 많아 {@link OpenAiFeedbackGenerator}가 이 타입만 재호출 대상으로
 * 삼는다. 프로바이더 실패는 이미 spring.ai의 HTTP 재시도를 거쳤고 400처럼 확정된 실패도 섞여 있어 제외한다.
 *
 * <p>{@link FeedbackGenerationException}을 상속하고 reason과 사용량을 그대로 실어 나르므로, 서비스와 예외
 * 핸들러는 이 구분을 알 필요가 없다.
 */
class FeedbackResponseException extends FeedbackGenerationException {

    FeedbackResponseException(String reason, String message, Throwable cause, List<LlmCallUsage> llmCalls) {
        super(reason, message, cause, llmCalls);
    }
}
