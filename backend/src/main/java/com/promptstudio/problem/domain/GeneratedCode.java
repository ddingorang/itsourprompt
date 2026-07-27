package com.promptstudio.problem.domain;

import java.util.List;

public record GeneratedCode(
        List<ProblemFile> files,
        String summary
) {
}
