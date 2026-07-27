package com.promptstudio.attempt.domain;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.List;

public record GeneratedCode(
        List<ProblemFile> files,
        String summary
) {
}
