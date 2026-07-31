package com.promptstudio.attempt.controller;

import com.promptstudio.attempt.controller.response.AttemptResponse;
import com.promptstudio.attempt.controller.response.CodeRunResponse;
import com.promptstudio.attempt.controller.response.FeedbackResponse;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.CodeRunView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.domain.LlmUsageSummary;
import com.promptstudio.attempt.domain.LlmUsageTotals;
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
                attempt.status(),
                toUsageResponse(attempt.usage())
        );
    }

    /**
     * 턴별 피드백 이전에 제출된 어템프트는 턴 피드백이 없으므로 turns를 비우고 전체 피드백만 내보낸다.
     *
     * <p>제출은 모든 턴에 피드백을 배정하거나 하나도 배정하지 않으므로 첫 턴만 보면 어느 쪽인지 알 수 있다.
     */
    public FeedbackResponse toFeedbackResponse(AttemptView attempt) {
        List<AttemptView.TurnView> turnViews = attempt.turns();
        List<FeedbackResponse.TurnFeedback> turns = new ArrayList<>();

        if (!turnViews.isEmpty() && turnViews.getFirst().feedback() != null) {
            for (int index = 0; index < turnViews.size(); index++) {
                turns.add(new FeedbackResponse.TurnFeedback(index + 1, turnViews.get(index).feedback()));
            }
        }

        return new FeedbackResponse(turns, attempt.feedback());
    }

    public CodeRunResponse toCodeRunResponse(CodeRunView run) {
        return new CodeRunResponse(
                run.id(),
                run.turnOrdinal(),
                run.status(),
                run.exitCode(),
                run.stdout(),
                run.stderr(),
                run.durationMs()
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

        return new AttemptResponse.TurnResponse(
                turn.userPrompt(), turn.aiSummary(), changedFiles, toolCalls, toUsageResponse(turn.usage()));
    }

    private AttemptResponse.TurnUsageResponse toUsageResponse(LlmUsageSummary usage) {
        if (usage == null) {
            return null;
        }

        return new AttemptResponse.TurnUsageResponse(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cachedInputTokens(),
                usage.reasoningTokens(),
                usage.cost(),
                usage.model(),
                usage.rounds()
        );
    }

    private AttemptResponse.AttemptUsageResponse toUsageResponse(LlmUsageTotals usage) {
        if (usage == null) {
            return null;
        }

        return new AttemptResponse.AttemptUsageResponse(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cachedInputTokens(),
                usage.reasoningTokens(),
                usage.cost()
        );
    }
}
