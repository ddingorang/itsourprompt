package com.promptstudio.attempt.controller;

import com.promptstudio.attempt.controller.response.AttemptResponse;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.problem.domain.ProblemFile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AttemptWebMapper {

    public AttemptResponse toAttemptResponse(AttemptView attempt) {
        List<AttemptResponse.TurnResponse> turns = new ArrayList<>();

        for (AttemptView.TurnView turn : attempt.turns()) {
            turns.add(toTurnResponse(turn));
        }

        return new AttemptResponse(
                attempt.id(),
                attempt.problemId(),
                toFileResponses(attempt.baseFiles()),
                toFileResponses(attempt.files()),
                turns,
                attempt.status()
        );
    }

    private List<AttemptResponse.AttemptFileResponse> toFileResponses(List<ProblemFile> files) {
        List<AttemptResponse.AttemptFileResponse> responses = new ArrayList<>();

        for (ProblemFile file : files) {
            responses.add(new AttemptResponse.AttemptFileResponse(file.path(), file.content()));
        }

        return responses;
    }

    private AttemptResponse.TurnResponse toTurnResponse(AttemptView.TurnView turn) {
        List<AttemptResponse.ChangedFileResponse> changedFiles = new ArrayList<>();

        for (FileChange change : turn.changes()) {
            changedFiles.add(new AttemptResponse.ChangedFileResponse(change.path(), change.type(), change.content()));
        }

        List<AttemptResponse.ToolCallResponse> toolCalls = new ArrayList<>();

        for (ToolCallEntry toolCall : turn.toolCalls()) {
            toolCalls.add(new AttemptResponse.ToolCallResponse(toolCall.tool(), toolCall.path()));
        }

        return new AttemptResponse.TurnResponse(turn.userPrompt(), turn.aiSummary(), changedFiles, toolCalls);
    }
}
