package com.promptstudio.ai.dto;

import com.promptstudio.problem.entity.ProblemFile;

import java.util.List;

public record AiCodeResult(
        List<ProblemFile> files,
        String aiResponse
) {
}
