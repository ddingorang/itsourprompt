package com.promptstudio.attempt.domain;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.ArrayList;
import java.util.List;

public record AttemptView(
        Long id,
        Long problemId,
        List<ProblemFile> files,
        List<TurnView> turns
) {

    public static AttemptView from(Attempt attempt) {
        List<TurnView> turns = new ArrayList<>();

        for (Turn turn : attempt.turns()) {
            turns.add(new TurnView(turn.userPrompt(), turn.aiSummary(), turn.changes()));
        }

        return new AttemptView(attempt.id(), attempt.problemId(), attempt.currentFiles(), List.copyOf(turns));
    }

    public record TurnView(
            String userPrompt,
            String aiSummary,
            List<FileChange> changes
    ) {
    }
}
