package com.promptstudio.ai;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGenerationPromptsTest {

    @Test
    void 사용자_프롬프트에_모든_파일과_요청을_포함한다() {
        Problem problem = new Problem(1L, "제목", "명세", List.of(
                new ProblemFile("src/Main.java", "class Main {}"),
                new ProblemFile("src/Util.java", "class Util {}")
        ));

        String prompt = CodeGenerationPrompts.userPrompt(problem, "Hello 출력해줘");

        assertThat(prompt)
                .contains("--- src/Main.java ---")
                .contains("class Main {}")
                .contains("--- src/Util.java ---")
                .contains("class Util {}")
                .contains("[사용자 요청]")
                .contains("Hello 출력해줘");
    }

    @Test
    void 시스템_프롬프트는_JSON_응답_계약을_명시한다() {
        String prompt = CodeGenerationPrompts.systemPrompt();

        assertThat(prompt)
                .contains("\"files\"")
                .contains("\"aiResponse\"");
    }
}
