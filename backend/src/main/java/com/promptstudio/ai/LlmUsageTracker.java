package com.promptstudio.ai;

import com.openai.models.completions.CompletionUsage;
import com.promptstudio.attempt.domain.LlmCallUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 요청 하나가 낸 LLM 호출들의 사용량을 호출 순서대로 쌓는다.
 *
 * <p>타임아웃이 나면 루프 스레드가 아직 도는 채로 호출 스레드가 누적분을 읽어가므로 쓰기와 스냅샷의
 * 스레드가 다르다.
 */
final class LlmUsageTracker {

    private final List<LlmCallUsage> calls = new CopyOnWriteArrayList<>();

    void record(String model, ChatResponse response, long latencyMs) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();

        calls.add(new LlmCallUsage(
                calls.size() + 1,
                model,
                usage == null ? null : toLong(usage.getPromptTokens()),
                usage == null ? null : toLong(usage.getCompletionTokens()),
                usage == null ? null : usage.getCacheReadInputTokens(),
                reasoningTokensOf(usage),
                latencyMs
        ));
    }

    List<LlmCallUsage> snapshot() {
        return List.copyOf(calls);
    }

    /**
     * reasoning 토큰은 Spring AI 공통 usage에 없고 OpenAI native usage에만 실린다.
     */
    private Long reasoningTokensOf(Usage usage) {
        if (usage == null || !(usage.getNativeUsage() instanceof CompletionUsage nativeUsage)) {
            return null;
        }

        return nativeUsage.completionTokensDetails()
                .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
                .orElse(null);
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
