package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.LlmCallUsage;

import java.util.List;

public class CodeGenerationTimeoutException extends RuntimeException implements LlmUsageCarrier {

    private static final String ERROR_TYPE = "timeout";

    private final List<LlmCallUsage> llmCalls;

    public CodeGenerationTimeoutException() {
        this(List.of());
    }

    public CodeGenerationTimeoutException(List<LlmCallUsage> llmCalls) {
        super("AI 코드 생성 요청 시간이 초과되었습니다.");
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
