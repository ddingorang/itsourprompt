package com.promptstudio.ai;

import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.attempt.port.CodeGenerationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedCodeParserTest {

    @Test
    void 올바른_JSON_응답을_GeneratedCode로_변환한다() {
        String raw = """
                {
                  "files": [{ "path": "src/Main.java", "content": "class Main {}" }],
                  "aiResponse": "Main.java를 수정했습니다."
                }
                """;

        GeneratedCode result = GeneratedCodeParser.parse(raw);

        assertThat(result.files()).containsExactly(new ProblemFile("src/Main.java", "class Main {}"));
        assertThat(result.summary()).isEqualTo("Main.java를 수정했습니다.");
    }

    @Test
    void 빈_응답이면_예외를_던진다() {
        assertThatThrownBy(() -> GeneratedCodeParser.parse("  "))
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("AI가 빈 응답을 반환했습니다.");
    }

    @Test
    void JSON이_아니면_예외를_던진다() {
        assertThatThrownBy(() -> GeneratedCodeParser.parse("마크다운 답변"))
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("AI가 올바른 JSON 형식의 응답을 반환하지 않았습니다.");
    }

    @Test
    void 파일_목록이_비어_있으면_예외를_던진다() {
        assertThatThrownBy(() -> GeneratedCodeParser.parse("{\"files\": [], \"aiResponse\": \"요약\"}"))
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("AI 응답에 최종 파일이 없습니다.");
    }

    @Test
    void 작업_요약이_없으면_예외를_던진다() {
        String raw = """
                {
                  "files": [{ "path": "a.java", "content": "x" }],
                  "aiResponse": ""
                }
                """;

        assertThatThrownBy(() -> GeneratedCodeParser.parse(raw))
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("AI 응답에 작업 요약이 없습니다.");
    }

    @Test
    void 절대_경로나_상위_경로는_거부한다() {
        String absolute = "{\"files\": [{\"path\": \"/etc/passwd\", \"content\": \"x\"}], \"aiResponse\": \"요약\"}";
        String traversal = "{\"files\": [{\"path\": \"../evil.java\", \"content\": \"x\"}], \"aiResponse\": \"요약\"}";

        assertThatThrownBy(() -> GeneratedCodeParser.parse(absolute))
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("AI 응답에 허용되지 않는 파일 경로가 있습니다.");
        assertThatThrownBy(() -> GeneratedCodeParser.parse(traversal))
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("AI 응답에 허용되지 않는 파일 경로가 있습니다.");
    }

    @Test
    void 중복_경로는_거부한다() {
        String raw = """
                {
                  "files": [
                    { "path": "a.java", "content": "1" },
                    { "path": "a.java", "content": "2" }
                  ],
                  "aiResponse": "요약"
                }
                """;

        assertThatThrownBy(() -> GeneratedCodeParser.parse(raw))
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("AI 응답에 중복된 파일 경로가 있습니다.");
    }

    @Test
    void 파일_내용이_null이면_거부한다() {
        String raw = "{\"files\": [{\"path\": \"a.java\"}], \"aiResponse\": \"요약\"}";

        assertThatThrownBy(() -> GeneratedCodeParser.parse(raw))
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("AI 응답에 파일 내용이 없습니다.");
    }
}
