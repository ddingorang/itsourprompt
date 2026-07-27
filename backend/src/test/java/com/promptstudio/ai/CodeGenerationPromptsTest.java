package com.promptstudio.ai;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.domain.Turn;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.problem.domain.ProblemFile;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGenerationPromptsTest {

    @Test
    void 시스템_프롬프트는_JSON_응답_계약을_명시한다() {
        String prompt = CodeGenerationPrompts.systemPrompt();

        assertThat(prompt)
                .contains("\"files\"")
                .contains("\"aiResponse\"");
    }

    @Test
    void 메시지_목록은_시스템_프롬프트로_시작한다() {
        List<Message> messages = CodeGenerationPrompts.messages(attemptWithoutTurns(), "Hello 출력해줘");

        assertThat(messages.getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.getFirst().getText()).isEqualTo(CodeGenerationPrompts.systemPrompt());
    }

    @Test
    void 턴_히스토리를_사용자와_어시스턴트_메시지로_변환한다() {
        Attempt attempt = new Attempt(
                1L,
                1L,
                List.of(new ProblemFile("src/Main.java", "class Main { void run() {} }")),
                List.of(
                        new Turn("첫 요청", "첫 요약", List.of(
                                new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED)
                        )),
                        new Turn("두 번째 요청", "두 번째 요약", List.of())
                )
        );

        List<Message> messages = CodeGenerationPrompts.messages(attempt, "세 번째 요청");

        assertThat(messages).hasSize(6);
        assertThat(messages.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(1).getText()).isEqualTo("첫 요청");
        assertThat(messages.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(messages.get(2).getText()).isEqualTo("첫 요약");
        assertThat(messages.get(3).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(3).getText()).isEqualTo("두 번째 요청");
        assertThat(messages.get(4).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(messages.get(4).getText()).isEqualTo("두 번째 요약");
    }

    @Test
    void 마지막_사용자_메시지에_현재_파일과_새_요청을_포함한다() {
        Attempt attempt = new Attempt(1L, 1L, List.of(
                new ProblemFile("src/Main.java", "class Main {}"),
                new ProblemFile("src/Util.java", "class Util {}")
        ), List.of());

        List<Message> messages = CodeGenerationPrompts.messages(attempt, "Hello 출력해줘");

        Message lastMessage = messages.getLast();
        assertThat(lastMessage.getMessageType()).isEqualTo(MessageType.USER);
        assertThat(lastMessage.getText())
                .contains("[현재 프로젝트 파일]")
                .contains("--- src/Main.java ---")
                .contains("class Main {}")
                .contains("--- src/Util.java ---")
                .contains("class Util {}")
                .contains("[사용자 요청]")
                .contains("Hello 출력해줘");
    }

    @Test
    void 턴이_없으면_시스템과_사용자_메시지만_생성한다() {
        List<Message> messages = CodeGenerationPrompts.messages(attemptWithoutTurns(), "Hello 출력해줘");

        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
    }

    private Attempt attemptWithoutTurns() {
        return new Attempt(1L, 1L, List.of(new ProblemFile("src/Main.java", "class Main {}")), List.of());
    }
}
