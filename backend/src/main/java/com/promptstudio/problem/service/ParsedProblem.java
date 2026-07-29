package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.List;

public record ParsedProblem(
        String slug,
        String title,
        String specMd,
        List<ProblemFile> files
) {
}
