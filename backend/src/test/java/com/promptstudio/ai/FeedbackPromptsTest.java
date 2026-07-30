package com.promptstudio.ai;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackPromptsTest {

    private final ProblemView problem = new ProblemView(1L, "제목", "명세", List.of());

    @Test
    void 사용자_프롬프트에_시작_스켈레톤_코드를_한_번_포함한다() {
        AttemptView attempt = new AttemptView(1L, 1L, List.of(
                new ProblemFile("src/Main.java", "class Main {}")
        ), List.of(), List.of(
                new AttemptView.TurnView("프롬프트", "요약", List.of(), List.of())
        ), AttemptStatus.IN_PROGRESS, null);

        String prompt = FeedbackPrompts.userPrompt(problem, attempt);

        assertThat(prompt).contains("[Skeleton files]\n--- src/Main.java ---\nclass Main {}");
    }

    @Test
    void 턴별_변경_파일에_변경_후_코드를_포함한다() {
        AttemptView attempt = new AttemptView(1L, 1L, List.of(), List.of(), List.of(
                new AttemptView.TurnView("프롬프트", "요약", List.of(
                        new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED, "class Main { void run() {} }"),
                        new FileChange("src/Old.java", FileChange.ChangeType.DELETED, null)
                ), List.of())
        ), AttemptStatus.IN_PROGRESS, null);

        String prompt = FeedbackPrompts.userPrompt(problem, attempt);

        assertThat(prompt)
                .contains("- MODIFIED: src/Main.java\nclass Main { void run() {} }")
                .contains("- DELETED: src/Old.java\n(file removed)");
    }

    @Test
    void 사용자_프롬프트에_문제_명세와_모든_턴_기록을_포함한다() {
        AttemptView attempt = new AttemptView(1L, 1L, List.of(), List.of(), List.of(
                new AttemptView.TurnView("첫 프롬프트", "첫 요약", List.of(
                        new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED, "class Main {}")
                ), List.of()),
                new AttemptView.TurnView("두 번째 프롬프트", "두 번째 요약", List.of(
                        new FileChange("src/Util.java", FileChange.ChangeType.ADDED, "class Util {}")
                ), List.of())
        ), AttemptStatus.IN_PROGRESS, null);

        String prompt = FeedbackPrompts.userPrompt(problem, attempt);

        assertThat(prompt)
                .contains("[Problem title]\n제목")
                .contains("[Problem specification]\n명세")
                .contains("[Turn 1 user prompt]\n첫 프롬프트")
                .contains("[Turn 1 AI work summary]\n첫 요약")
                .contains("[Turn 1 changed files]\n- MODIFIED: src/Main.java")
                .contains("[Turn 2 user prompt]\n두 번째 프롬프트")
                .contains("[Turn 2 AI work summary]\n두 번째 요약")
                .contains("[Turn 2 changed files]\n- ADDED: src/Util.java");
    }

    @Test
    void 턴에_변경_파일이_없으면_표시_문구를_넣는다() {
        AttemptView attempt = new AttemptView(1L, 1L, List.of(), List.of(), List.of(
                new AttemptView.TurnView("프롬프트", "요약", List.of(), List.of())
        ), AttemptStatus.IN_PROGRESS, null);

        String prompt = FeedbackPrompts.userPrompt(problem, attempt);

        assertThat(prompt).contains("[Turn 1 changed files]\n(no changed files)");
    }
}
