package com.promptstudio.attempt.domain;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.List;

/**
 * @param llmCalls 이 턴을 만드는 데 든 LLM 호출들의 사용량 (라운드 순서)
 */
public record GeneratedCode(
        List<ProblemFile> files,
        String summary,
        List<ToolCallEntry> toolCalls,
        List<LlmCallUsage> llmCalls
) {

    public GeneratedCode {
        llmCalls = List.copyOf(llmCalls);
    }
}
