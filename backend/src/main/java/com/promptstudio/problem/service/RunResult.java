package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.FileChange;
import com.promptstudio.problem.domain.ProblemFile;

import java.util.List;

public record RunResult(
        List<ProblemFile> files,
        List<FileChange> changes,
        String summary
) {
}
