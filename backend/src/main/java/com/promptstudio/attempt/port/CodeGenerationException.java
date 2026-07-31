package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.LlmCallUsage;

import java.util.List;

public class CodeGenerationException extends RuntimeException implements LlmUsageCarrier {

    private static final String DEFAULT_ERROR_TYPE = "provider-error";

    private final String errorType;
    private final List<LlmCallUsage> llmCalls;

    public CodeGenerationException(String message) {
        this(message, null, DEFAULT_ERROR_TYPE, List.of());
    }

    public CodeGenerationException(String message, Throwable cause) {
        this(message, cause, DEFAULT_ERROR_TYPE, List.of());
    }

    public CodeGenerationException(String message, Throwable cause, String errorType, List<LlmCallUsage> llmCalls) {
        super(message, cause);
        this.errorType = errorType;
        this.llmCalls = List.copyOf(llmCalls);
    }

    @Override
    public List<LlmCallUsage> llmCalls() {
        return llmCalls;
    }

    @Override
    public String errorType() {
        return errorType;
    }
}
