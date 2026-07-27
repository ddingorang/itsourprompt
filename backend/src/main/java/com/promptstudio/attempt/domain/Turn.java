package com.promptstudio.attempt.domain;

import java.util.List;

public record Turn(
        String userPrompt,
        String aiSummary,
        List<FileChange> changes
) {
}
