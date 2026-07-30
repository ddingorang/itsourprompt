package com.promptstudio.attempt.domain;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @param baseFiles 시작 스켈레톤
 * @param files     턴을 재생한 현재 상태
 */
public record AttemptView(
        Long id,
        Long problemId,
        List<ProblemFile> baseFiles,
        List<ProblemFile> files,
        List<TurnView> turns,
        AttemptStatus status,
        String feedback
) {

    public static AttemptView from(Attempt attempt) {
        List<TurnView> turns = new ArrayList<>();

        for (Turn turn : attempt.turns()) {
            turns.add(new TurnView(
                    turn.userPrompt(), turn.aiSummary(), turn.changes(), turn.toolCalls(), turn.feedback()));
        }

        return reconstruct(
                attempt.id(),
                attempt.problemId(),
                attempt.baseFiles(),
                turns,
                attempt.status(),
                attempt.feedback()
        );
    }

    /**
     * 현재 상태(files)는 저장하지 않으므로 base에 턴을 재생해 만든다. 파생은 여기 한 곳에서만 한다.
     */
    public static AttemptView reconstruct(
            Long id,
            Long problemId,
            List<ProblemFile> baseFiles,
            List<TurnView> turns,
            AttemptStatus status,
            String feedback
    ) {
        return new AttemptView(
                id,
                problemId,
                List.copyOf(baseFiles),
                FileReplay.head(baseFiles, turns, TurnView::changes),
                List.copyOf(turns),
                status,
                feedback
        );
    }

    /**
     * @param feedback 제출 전이거나 턴별 피드백 이전에 제출된 어템프트면 null
     */
    public record TurnView(
            String userPrompt,
            String aiSummary,
            List<FileChange> changes,
            List<ToolCallEntry> toolCalls,
            String feedback
    ) {
    }
}
