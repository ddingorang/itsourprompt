package com.promptstudio.ai;

import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.problem.domain.ProblemFile;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 툴은 매니저와 같은 경로(JSON 인자 문자열)로 구동해 인자명·역직렬화까지 함께 검증한다.
 * 결과는 JSON 직렬화되므로 따옴표를 피해 contains로 단언한다.
 */
class CodeGenerationToolsTest {

    private final List<ProblemFile> files = List.of(
            new ProblemFile("src/Main.java", "class Main {}"),
            new ProblemFile("src/Util.java", "class Util {}")
    );

    private final CodeGenerationTools tools = new CodeGenerationTools(files);

    @Test
    void 툴은_list_files_read_file_edit_file_세_개다() {
        assertThat(tools.callbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("list_files", "read_file", "edit_file");
    }

    @Test
    void list_files는_모든_파일_경로를_순서대로_반환한다() {
        String result = call("list_files", "{}");

        assertThat(result).contains("src/Main.java").contains("src/Util.java");
        assertThat(result.indexOf("src/Main.java")).isLessThan(result.indexOf("src/Util.java"));
    }

    @Test
    void read_file은_파일_내용을_반환한다() {
        assertThat(call("read_file", "{\"path\":\"src/Main.java\"}")).contains("class Main {}");
    }

    @Test
    void read_file은_없는_경로면_에러_문자열을_반환한다() {
        assertThat(call("read_file", "{\"path\":\"src/None.java\"}"))
                .contains("파일을 찾을 수 없습니다")
                .contains("src/None.java");
    }

    @Test
    void edit_file은_작업본_내용을_전체_교체한다() {
        call("edit_file", "{\"path\":\"src/Main.java\",\"content\":\"class Main { void run() {} }\"}");

        assertThat(tools.currentFiles()).containsExactly(
                new ProblemFile("src/Main.java", "class Main { void run() {} }"),
                new ProblemFile("src/Util.java", "class Util {}")
        );
    }

    @Test
    void edit_file은_없는_경로면_에러를_반환하고_파일을_만들지_않는다() {
        String result = call("edit_file", "{\"path\":\"src/New.java\",\"content\":\"class New {}\"}");

        assertThat(result).contains("파일을 찾을 수 없습니다");
        assertThat(tools.currentFiles())
                .extracting(ProblemFile::path)
                .containsExactly("src/Main.java", "src/Util.java");
    }

    @Test
    void edit_file은_외부_import가_있으면_파일을_수정하지_않는다() {
        String result = call("edit_file", "{\"path\":\"src/Main.java\",\"content\":\"import lombok.Getter;\\nclass Main {}\"}");

        assertThat(result).contains("Java 표준 라이브러리");
        assertThat(tools.currentFiles()).containsExactly(files.toArray(ProblemFile[]::new));
    }

    @Test
    void edit_file은_java_import를_허용한다() {
        String result = call("edit_file", "{\"path\":\"src/Main.java\",\"content\":\"import java.util.List;\\nclass Main {}\"}");

        assertThat(result).contains("ok");
    }

    /**
     * java의 import 정적 검증은 python 코드를 오탐한다(python의 import 문은 세미콜론이 없어
     * 걸리지 않지만, 정책 메시지가 Java 기준이라 성립하지 않는다). python 문제의 편집은
     * import 검증 없이 통과하고, 외부 패키지 시도는 채점에서 ModuleNotFoundError로 드러난다.
     */
    @Test
    void python_문제의_edit_file은_import_검증_없이_수정을_적용한다() {
        CodeGenerationTools pythonTools = new CodeGenerationTools(
                List.of(new ProblemFile("src/main/python/report.py", "def top_words(text): ...")), "python");

        String result = call(pythonTools, "edit_file",
                "{\"path\":\"src/main/python/report.py\",\"content\":\"import collections\\n\\ndef top_words(text): ...\"}");

        assertThat(result).contains("ok");
    }

    @Test
    void python_문제도_패키징_파일은_보호된다() {
        CodeGenerationTools pythonTools = new CodeGenerationTools(
                List.of(new ProblemFile("requirements.txt", "")), "python");

        String result = call(pythonTools, "edit_file", "{\"path\":\"requirements.txt\",\"content\":\"numpy\"}");

        assertThat(result).contains("변경할 수 없습니다");
    }

    @Test
    void 모든_툴_호출은_실패해도_트레이스에_순서대로_기록된다() {
        call("list_files", "{}");
        call("read_file", "{\"path\":\"src/None.java\"}");
        call("edit_file", "{\"path\":\"src/Main.java\",\"content\":\"수정한 내용\"}");

        assertThat(tools.trace()).containsExactly(
                new ToolCallEntry("list_files", null),
                new ToolCallEntry("read_file", "src/None.java"),
                new ToolCallEntry("edit_file", "src/Main.java")
        );
    }

    @Test
    void 트레이스의_path는_저장_컬럼_한도인_500자로_절단된다() {
        String longPath = "a".repeat(600);

        String result = call("edit_file", "{\"path\":\"" + longPath + "\",\"content\":\"내용\"}");

        assertThat(result).contains("파일을 찾을 수 없습니다");
        assertThat(tools.trace().getFirst().path()).hasSize(500);
    }

    @Test
    void editedFileCount는_성공한_편집만_센다() {
        call("edit_file", "{\"path\":\"src/Main.java\",\"content\":\"수정 1\"}");
        call("edit_file", "{\"path\":\"src/Main.java\",\"content\":\"수정 2\"}");
        call("edit_file", "{\"path\":\"src/None.java\",\"content\":\"거부됨\"}");

        assertThat(tools.editedFileCount()).isEqualTo(1);
    }

    @Test
    void 작업본은_원본_리스트를_변경하지_않는다() {
        call("edit_file", "{\"path\":\"src/Main.java\",\"content\":\"수정한 내용\"}");

        assertThat(files).containsExactly(
                new ProblemFile("src/Main.java", "class Main {}"),
                new ProblemFile("src/Util.java", "class Util {}")
        );
    }

    private String call(String name, String jsonArguments) {
        return call(tools, name, jsonArguments);
    }

    private String call(CodeGenerationTools target, String name, String jsonArguments) {
        for (ToolCallback callback : target.callbacks()) {
            if (callback.getToolDefinition().name().equals(name)) {
                return callback.call(jsonArguments);
            }
        }

        throw new IllegalArgumentException("툴을 찾을 수 없습니다: " + name);
    }
}
