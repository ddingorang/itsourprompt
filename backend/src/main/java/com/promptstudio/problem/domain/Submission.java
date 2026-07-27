package com.promptstudio.problem.domain;

import java.util.List;

public record Submission(
        String prompt,
        String aiSummary,
        List<FileChange> changes
) {
}
