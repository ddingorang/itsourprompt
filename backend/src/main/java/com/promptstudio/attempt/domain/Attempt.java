package com.promptstudio.attempt.domain;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;

import java.util.ArrayList;
import java.util.List;

public record Attempt(
        Long id,
        Long problemId,
        List<ProblemFile> currentFiles,
        List<Turn> turns
) {

    public static Attempt start(Problem problem) {
        return new Attempt(null, problem.id(), List.copyOf(problem.files()), List.of());
    }

    public Attempt applyTurn(String userPrompt, GeneratedCode generated) {
        List<FileChange> changes = FileChanges.diff(currentFiles, generated.files());
        List<Turn> appendedTurns = new ArrayList<>(turns);
        appendedTurns.add(new Turn(userPrompt, generated.summary(), changes));

        return new Attempt(id, problemId, List.copyOf(generated.files()), List.copyOf(appendedTurns));
    }
}
