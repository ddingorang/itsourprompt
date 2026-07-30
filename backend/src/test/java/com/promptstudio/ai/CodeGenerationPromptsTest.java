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
        String prompt = CodeGenerationPrompts.systemPrompt();

        assertThat(prompt)
                .contains("list_files")
                .contains("read_file")
                .contains("edit_file")
                .contains("새 파일")
                .doesNotContain("aiResponse");
    }

    @Test
    void 메시지_목록은_시스템_프롬프트로_시작한다() {
        List<Message> messages = CodeGenerationPrompts.messages(problem, attemptWithoutTurns(), "Hello 출력해줘");

        assertThat(messages.getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.getFirst().getText()).isEqualTo(CodeGenerationPrompts.systemPrompt());
    }

    @Test
    void 마지막_사용자_메시지는_문제_명세와_요청만_담는다() {
        Message lastMessage = CodeGenerationPrompts.messages(problem, attemptWithoutTurns(), "Hello 출력해줘")
                .getLast();

        assertThat(lastMessage.getMessageType()).isEqualTo(MessageType.USER);
        assertThat(lastMessage.getText())
                .contains("[문제 명세]")
                .contains("# Hello World 출력")
                .contains("[사용자 요청]")
                .contains("Hello 출력해줘")
                .doesNotContain("src/Main.java")
                .doesNotContain("class Main {}");
    }

    @Test
    void 턴_히스토리의_어시스턴트_메시지에_수정한_파일_목록을_덧붙인다() {
        AttemptView attempt = new AttemptView(1L, 1L, List.of(), List.of(), List.of(
                new AttemptView.TurnView("첫 요청", "첫 요약", List.of(
                        new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED, "class Main { void run() {} }")
                ), List.of(), null)
        ), AttemptStatus.IN_PROGRESS, null);

        List<Message> messages = CodeGenerationPrompts.messages(problem, attempt, "두 번째 요청");

        assertThat(messages).hasSize(4);
        assertThat(messages.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(1).getText()).isEqualTo("첫 요청");
        assertThat(messages.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(messages.get(2).getText()).isEqualTo("첫 요약\n[수정한 파일] src/Main.java");
    }

    @Test
    void 변경이_없는_턴은_수정한_파일_목록_줄을_생략한다() {
        AttemptView attempt = new AttemptView(1L, 1L, List.of(), List.of(), List.of(
                new AttemptView.TurnView("첫 요청", "첫 요약", List.of(), List.of(), null)
        ), AttemptStatus.IN_PROGRESS, null);

        List<Message> messages = CodeGenerationPrompts.messages(problem, attempt, "두 번째 요청");

        assertThat(messages.get(2).getText()).isEqualTo("첫 요약");
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
                List.of(), AttemptStatus.IN_PROGRESS, null);
    }
}
