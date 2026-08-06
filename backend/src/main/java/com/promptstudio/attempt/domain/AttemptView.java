package com.promptstudio.attempt.domain;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @param owner           이 풀이의 주인. 응답에는 나가지 않는다 — 남의 사용자 ID·세션 ID를 공개 조회로
 *                        흘리면 안 된다. mine 판정과 표시 이름 조립에만 쓴다.
 *                        소유자 없는 과거 기록은 null이다
 * @param nickname        주인이 로그인 사용자일 때의 닉네임. 게스트면 null이고, 조인이 없는 엔티티
 *                        경로({@link #from})는 USER여도 null이다
 * @param baseFiles       시작 스켈레톤
 * @param files           턴을 재생한 현재 상태
 * @param patternFeedback 세션 전체 pattern 피드백. pattern 피드백 이전에 제출된 어템프트면 null
 * @param usage           어템프트의 턴 합계. 턴 기록이 없으면 null
 */
public record AttemptView(
        Long id,
        Long problemId,
        AttemptOwner owner,
        String nickname,
        List<ProblemFile> baseFiles,
        List<ProblemFile> files,
        List<TurnView> turns,
        AttemptStatus status,
        String feedback,
        String patternFeedback,
        LlmUsageTotals usage
) {

    /**
     * 사용량은 호출 행에서 파생하는데 엔티티는 그 행을 들고 있지 않으므로 항상 null이다 —
     * 사용량이 필요한 경로는 조회 seam(jOOQ)을 쓴다.
     */
    public static AttemptView from(Attempt attempt) {
        List<TurnView> turns = new ArrayList<>();

        for (Turn turn : attempt.turns()) {
            turns.add(new TurnView(
                    turn.userPrompt(),
                    turn.aiSummary(),
                    turn.changes(),
                    turn.toolCalls(),
                    turn.feedback(),
                    turn.patternFeedback(),
                    null
            ));
        }

        // 소유자 컬럼이 생기기 전 행은 둘 다 비어 있다(schema.sql의 chk_attempt_exactly_one_owner는
        // NOT VALID다). AttemptOwner 생성자가 그 조합을 거부하므로 여기서 null로 남긴다.
        AttemptOwner owner = attempt.userId() == null && attempt.guestSessionId() == null
                ? null
                : new AttemptOwner(attempt.userId(), attempt.guestSessionId());

        return reconstruct(
                attempt.id(),
                attempt.problemId(),
                owner,
                null,
                attempt.baseFiles(),
                turns,
                attempt.status(),
                attempt.feedback(),
                attempt.patternFeedback(),
                null
        );
    }

    /**
     * 현재 상태(files)는 저장하지 않으므로 base에 턴을 재생해 만든다. 파생은 여기 한 곳에서만 한다.
     */
    public static AttemptView reconstruct(
            Long id,
            Long problemId,
            AttemptOwner owner,
            String nickname,
            List<ProblemFile> baseFiles,
            List<TurnView> turns,
            AttemptStatus status,
            String feedback,
            String patternFeedback,
            LlmUsageTotals usage
    ) {
        return new AttemptView(
                id,
                problemId,
                owner,
                nickname,
                List.copyOf(baseFiles),
                FileReplay.head(baseFiles, turns, TurnView::changes),
                List.copyOf(turns),
                status,
                feedback,
                patternFeedback,
                usage
        );
    }

    /**
     * turnOrdinal번째 턴까지 재생한 코드. 턴은 불변이라 같은 ordinal은 언제 물어도 같은 코드를 낸다.
     *
     * @param turnOrdinal 0-based 턴 번호. null이면 턴을 적용하지 않은 시작 스켈레톤
     * @throws IndexOutOfBoundsException 없는 턴 번호. 호출자가 미리 검증한다
     */
    public List<ProblemFile> filesAsOf(Integer turnOrdinal) {
        if (turnOrdinal == null) {
            return baseFiles;
        }

        return FileReplay.head(baseFiles, turns.subList(0, turnOrdinal + 1), TurnView::changes);
    }

    /**
     * 마지막 턴의 번호. 턴이 없으면 null이고, 그때 {@link #filesAsOf}는 스켈레톤을 낸다.
     */
    public Integer headTurnOrdinal() {
        return turns.isEmpty() ? null : turns.size() - 1;
    }

    /**
     * @param feedback        제출 전이거나 턴별 피드백 이전에 제출된 어템프트면 null
     * @param patternFeedback 제출 전이거나 pattern 피드백 이전에 제출된 어템프트면 null
     * @param usage           이 턴의 LLM 사용량 합계. 사용량 기록 도입 전 턴이면 null
     */
    public record TurnView(
            String userPrompt,
            String aiSummary,
            List<FileChange> changes,
            List<ToolCallEntry> toolCalls,
            String feedback,
            String patternFeedback,
            LlmUsageSummary usage
    ) {
    }
}
