package com.promptstudio.problem.domain;

import java.util.List;

public record ProblemView(
        Long id,
        String title,
        String specMd,
        String type,
        String language,
        List<ProblemFile> files
) {

    public ProblemView(Long id, String title, String specMd, List<ProblemFile> files) {
        this(id, title, specMd, "coding", "java", files);
    }

    public ProblemView(Long id, String title, String specMd, String type, List<ProblemFile> files) {
        this(id, title, specMd, type, "java", files);
    }

    public static ProblemView from(Problem problem) {
        return new ProblemView(
                problem.id(), problem.title(), problem.specMd(), problem.type(), problem.language(), problem.files());
    }
}
