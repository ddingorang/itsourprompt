package com.promptstudio.problem.domain;

import java.util.List;

public record ProblemView(
        Long id,
        String title,
        String specMd,
        List<ProblemFile> files
) {

    public static ProblemView from(Problem problem) {
        return new ProblemView(problem.id(), problem.title(), problem.specMd(), problem.files());
    }
}
