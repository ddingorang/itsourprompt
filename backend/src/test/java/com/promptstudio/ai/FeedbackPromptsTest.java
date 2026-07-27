package com.promptstudio.ai;

import com.promptstudio.problem.domain.FileChange;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.Submission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackPromptsTest {

    private final Problem problem = new Problem(1L, "제목", "명세", List.of());

    @Test
    void 사용자_프롬프트에_제출_정보를_모두_포함한다() {
        Submission submission = new Submission(
                "프롬프트 원문",
                "작업 요약",
                List.of(new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED))
        );

        String prompt = FeedbackPrompts.userPrompt(problem, submission);

        assertThat(prompt)
                .contains("[Problem title]\n제목")
                .contains("[Problem specification]\n명세")
                .contains("[User prompt]\n프롬프트 원문")
                .contains("[AI work summary]\n작업 요약")
                .contains("- MODIFIED: src/Main.java");
    }

    @Test
    void 변경_파일이_없으면_표시_문구를_넣는다() {
        Submission submission = new Submission("프롬프트", "요약", List.of());

        String prompt = FeedbackPrompts.userPrompt(problem, submission);

        assertThat(prompt).contains("(no changed files)");
    }
}
