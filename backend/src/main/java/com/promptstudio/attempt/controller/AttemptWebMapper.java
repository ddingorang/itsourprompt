package com.promptstudio.attempt.controller;

import com.promptstudio.attempt.controller.response.AttemptResponse;
import com.promptstudio.attempt.controller.response.CodeRunListResponse;
import com.promptstudio.attempt.controller.response.CodeRunResponse;
import com.promptstudio.attempt.controller.response.FeedbackResponse;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.CodeRunCase;
import com.promptstudio.attempt.domain.CodeRunCaseTally;
import com.promptstudio.attempt.domain.CodeRunSummary;
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
     *
     * <p>가드는 프롬프트 피드백만 본다. 두 스타일 모두 필수라 pattern만 있는 상태는 생기지 않고,
     * pattern 도입 이전에 제출된 어템프트는 pattern 자리가 null로 나간다.
     */
    public FeedbackResponse toFeedbackResponse(AttemptView attempt) {
        List<AttemptView.TurnView> turnViews = attempt.turns();
        List<FeedbackResponse.TurnFeedback> turns = new ArrayList<>();

        if (!turnViews.isEmpty() && turnViews.getFirst().feedback() != null) {
            for (int index = 0; index < turnViews.size(); index++) {
                AttemptView.TurnView turn = turnViews.get(index);

                turns.add(new FeedbackResponse.TurnFeedback(index + 1, turn.feedback(), turn.patternFeedback()));
            }
        }

        return new FeedbackResponse(turns, attempt.feedback(), attempt.patternFeedback());
    }

    public CodeRunListResponse toCodeRunListResponse(List<CodeRunSummary> runs) {
        List<CodeRunListResponse.CodeRunSummaryResponse> responses = new ArrayList<>();

        for (CodeRunSummary run : runs) {
            responses.add(new CodeRunListResponse.CodeRunSummaryResponse(
                    run.id(),
                    run.turnOrdinal(),
                    run.status(),
                    run.exitCode(),
                    run.durationMs(),
                    run.createdAt(),
                    run.finishedAt(),
                    toTallyResponse(run.tally())
            ));
        }

        return new CodeRunListResponse(responses);
    }

    private CodeRunListResponse.CodeRunCaseTallyResponse toTallyResponse(CodeRunCaseTally tally) {
        if (tally == null) {
            return null;
        }

        return new CodeRunListResponse.CodeRunCaseTallyResponse(
                tally.total(), tally.passed(), tally.failed(), tally.error(), tally.skipped());
    }

    public CodeRunResponse toCodeRunResponse(CodeRunView run) {
        List<CodeRunResponse.CodeRunCaseResponse> cases = new ArrayList<>();

        for (CodeRunCase testCase : run.cases()) {
            cases.add(new CodeRunResponse.CodeRunCaseResponse(
                    testCase.className(),
                    testCase.name(),
                    testCase.status(),
                    testCase.message(),
                    testCase.durationMs()
            ));
        }

        return new CodeRunResponse(
                run.id(),
                run.turnOrdinal(),
                run.status(),
                run.exitCode(),
                run.stdout(),
                run.stderr(),
                run.durationMs(),
                cases
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
                usage.uncachedInputTokens(),
                usage.cachedInputTokens(),
                usage.outputTokens(),
                usage.reasoningTokens(),
                usage.latencyMs(),
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
                usage.uncachedInputTokens(),
                usage.cachedInputTokens(),
                usage.outputTokens(),
                usage.reasoningTokens(),
                usage.latencyMs(),
                usage.cost(),
                usage.rounds()
        );
    }
}
