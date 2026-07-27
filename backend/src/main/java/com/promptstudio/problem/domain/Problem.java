package com.promptstudio.problem.domain;

import java.util.List;

public record Problem(
        Long id,
        String title,
        String specMd,
        List<ProblemFile> files
) {
}
