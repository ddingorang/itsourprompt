package com.promptstudio.ai;

import com.promptstudio.attempt.domain.LlmCallUsage;

import java.util.List;

/**
 * 생성기 하나의 산출물. 도메인 {@link com.promptstudio.attempt.domain.AttemptFeedback}은 두 draft를 합쳐 만든다.
 *
 * @param llmCalls 이 draft를 만드는 데 든 호출들의 사용량. seq는 이 생성기 안에서 1부터다
 */
record FeedbackDraft(List<String> turnFeedbacks, String overall, List<LlmCallUsage> llmCalls) {
}
