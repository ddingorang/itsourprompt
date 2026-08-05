package com.promptstudio.ai;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGenerationPromptsTest {

    private final ProblemView problem = new ProblemView(1L, "제목", "# Hello World 출력", List.of());

    @Test
    void 시스템_프롬프트는_툴_사용과_새_파일_금지를_명시한다() {
        String prompt = CodeGenerationPrompts.systemPrompt("java");

        assertThat(prompt)
                .contains("list_files")
                .contains("read_file")
                .contains("edit_file")
                .contains("새 파일")
                .doesNotContain("aiResponse");
    }

    @Test
    void java_문제의_시스템_프롬프트는_java_제약을_담는다() {
        assertThat(CodeGenerationPrompts.systemPrompt("java"))
                .contains("Java 코드 생성 도우미")
                .contains("Java 표준 라이브러리")
                .doesNotContain("Python");
    }

    @Test
    void python_문제의_시스템_프롬프트는_python_제약을_담는다() {
        assertThat(CodeGenerationPrompts.systemPrompt("python"))
                .contains("Python 코드 생성 도우미")
                .contains("Python 표준 라이브러리")
                .contains("requirements.txt")
                .doesNotContain("Java 표준 라이브러리");
    }

    /** 언어 필드 도입 전의 문제는 언어가 null이며 java로 해석한다. */
    @Test
    void 언어가_null이면_java_프롬프트를_쓴다() {
        assertThat(CodeGenerationPrompts.systemPrompt(null)).isEqualTo(CodeGenerationPrompts.systemPrompt("java"));
    }

    @Test
    void 메시지_목록은_문제_언어의_시스템_프롬프트로_시작한다() {
        List<Message> messages = CodeGenerationPrompts.messages(problem, attemptWithoutTurns(), "Hello 출력해줘");

        assertThat(messages.getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.getFirst().getText()).isEqualTo(CodeGenerationPrompts.systemPrompt(problem.language()));
    }

    @Test
    void python_문제의_메시지는_python_시스템_프롬프트로_시작한다() {
        ProblemView pythonProblem = new ProblemView(2L, "제목", "# 명세", "coding", "python", List.of());

        List<Message> messages =
                CodeGenerationPrompts.messages(pythonProblem, attemptWithoutTurns(), "구현해줘");

        assertThat(messages.getFirst().getText()).contains("Python 코드 생성 도우미");
    }

    @Test
    void 마지막_사용자_메시지는_현재_요청만_담고_문제_명세는_제외한다() {
        Message lastMessage = CodeGenerationPrompts.messages(problem, attemptWithoutTurns(), "Hello 출력해줘")
                .getLast();

        assertThat(lastMessage.getMessageType()).isEqualTo(MessageType.USER);
        assertThat(lastMessage.getText())
                .contains("[사용자 요청]")
                .contains("Hello 출력해줘")
                .doesNotContain("[문제 명세]")
                .doesNotContain("# Hello World 출력")
                .doesNotContain("src/Main.java")
                .doesNotContain("class Main {}");
    }

    @Test
    void 수정한_파일이_있는_이전_턴도_코드_생성_메시지에_포함하지_않는다() {
        AttemptView attempt = new AttemptView(1L, 1L, List.of(), List.of(), List.of(
                new AttemptView.TurnView("첫 요청", "첫 요약", List.of(
                        new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED, "class Main { void run() {} }")
                ), List.of(), null, null, null)
        ), AttemptStatus.IN_PROGRESS, null, null, null);

        List<Message> messages = CodeGenerationPrompts.messages(problem, attempt, "두 번째 요청");

        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(messages.getLast().getText())
                .contains("두 번째 요청")
                .doesNotContain("첫 요청")
                .doesNotContain("첫 요약")
                .doesNotContain("src/Main.java");
    }

    @Test
    void 변경이_없는_이전_턴도_코드_생성_메시지에_포함하지_않는다() {
        AttemptView attempt = new AttemptView(1L, 1L, List.of(), List.of(), List.of(
                new AttemptView.TurnView("첫 요청", "첫 요약", List.of(), List.of(), null, null, null)
        ), AttemptStatus.IN_PROGRESS, null, null, null);

        List<Message> messages = CodeGenerationPrompts.messages(problem, attempt, "두 번째 요청");

        assertThat(messages).hasSize(2);
        assertThat(messages.getLast().getText())
                .contains("두 번째 요청")
                .doesNotContain("첫 요청")
                .doesNotContain("첫 요약");
    }

    @Test
    void 턴이_없으면_시스템과_사용자_메시지만_생성한다() {
        List<Message> messages = CodeGenerationPrompts.messages(problem, attemptWithoutTurns(), "Hello 출력해줘");

        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
    }

    private AttemptView attemptWithoutTurns() {
        return new AttemptView(1L, 1L, List.of(), List.of(new ProblemFile("src/Main.java", "class Main {}")),
                List.of(), AttemptStatus.IN_PROGRESS, null, null, null);
    }
}
