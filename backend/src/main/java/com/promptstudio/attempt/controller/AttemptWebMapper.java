package com.promptstudio.attempt.controller;

import com.promptstudio.attempt.controller.response.AttemptResponse;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.problem.domain.ProblemFile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AttemptWebMapper {

    public AttemptResponse toAttemptResponse(AttemptView attempt) {
        List<AttemptResponse.AttemptFileResponse> files = new ArrayList<>();

        for (ProblemFile file : attempt.files()) {
            files.add(new AttemptResponse.AttemptFileResponse(file.path(), file.content()));
        }

        List<AttemptResponse.TurnResponse> turns = new ArrayList<>();

        for (AttemptView.TurnView turn : attempt.turns()) {
            turns.add(toTurnResponse(turn));
        }

        return new AttemptResponse(attempt.id(), attempt.problemId(), files, turns);
    }

    private AttemptResponse.TurnResponse toTurnResponse(AttemptView.TurnView turn) {
        List<AttemptResponse.ChangedFileResponse> changedFiles = new ArrayList<>();

        for (FileChange change : turn.changes()) {
            changedFiles.add(new AttemptResponse.ChangedFileResponse(change.path(), change.type()));
        }

        return new AttemptResponse.TurnResponse(turn.userPrompt(), turn.aiSummary(), changedFiles);
    }
}
