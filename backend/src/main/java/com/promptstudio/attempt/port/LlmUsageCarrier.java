package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.LlmCallUsage;

import java.util.List;

/**
 * 실패한 AI 호출이 그때까지 누적한 사용량을 운반한다.
 *
 * <p>중간에 실패해도 이미 끝난 호출의 토큰은 과금되므로 예외에 실어 기록까지 보낸다.
 */
public interface LlmUsageCarrier {

    List<LlmCallUsage> llmCalls();

    /**
     * 실패 원인 분류. attempt_llm_call.error_type에 그대로 저장된다.
     */
    String errorType();
}
