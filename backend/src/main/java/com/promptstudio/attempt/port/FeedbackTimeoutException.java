package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.LlmCallUsage;

import java.util.List;

public class FeedbackTimeoutException extends RuntimeException implements LlmUsageCarrier {

    private static final String ERROR_TYPE = "timeout";

    private final List<LlmCallUsage> llmCalls;

    public FeedbackTimeoutException() {
        this(List.of());
    }

    public FeedbackTimeoutException(List<LlmCallUsage> llmCalls) {
        super("AI feedback request timed out.");
        this.llmCalls = List.copyOf(llmCalls);
    }

    @Override
    public List<LlmCallUsage> llmCalls() {
        return llmCalls;
    }

    @Override
    public String errorType() {
        return ERROR_TYPE;
    }
}
