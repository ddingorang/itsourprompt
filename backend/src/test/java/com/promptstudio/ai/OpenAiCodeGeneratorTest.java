package com.promptstudio.ai;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.attempt.port.CodeGenerationException;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCodeGeneratorTest {

    private final ProblemView problem = new ProblemView(1L, "제목", "# 명세", List.of());

    private final StubChatModel chatModel = new StubChatModel();

    private final OpenAiCodeGenerator codeGenerator = new OpenAiCodeGenerator(
            chatModel,
            ToolCallingManager.builder().build(),
            new AiCallExecutor(),
            new OpenAiChatOptionsFactory("code-model", "feedback-model")
    );

    @Test
    void 툴콜_없는_응답이면_한_번의_호출로_요약을_반환한다() {
        chatModel.queue(textResponse("구조는 이렇습니다."));

        GeneratedCode generated = codeGenerator.generate(problem, attempt(), "구조 설명해줘");

        assertThat(chatModel.receivedPrompts()).hasSize(1);
        assertThat(generated.files()).containsExactly(new ProblemFile("src/Main.java", "class Main {}"));
        assertThat(generated.toolCalls()).isEmpty();
        assertThat(generated.summary()).isEqualTo("구조는 이렇습니다.");
    }

    @Test
    void 툴콜_라운드를_반복해_편집을_작업본에_반영한다() {
        chatModel.queue(toolCallResponse("edit_file", "{\"path\":\"src/Main.java\",\"content\":\"수정된 내용\"}"));
        chatModel.queue(textResponse("Main을 수정했습니다."));

        GeneratedCode generated = codeGenerator.generate(problem, attempt(), "고쳐줘");

        assertThat(chatModel.receivedPrompts()).hasSize(2);
        assertThat(generated.files()).containsExactly(new ProblemFile("src/Main.java", "수정된 내용"));
        assertThat(generated.toolCalls()).containsExactly(new ToolCallEntry("edit_file", "src/Main.java"));
        assertThat(generated.summary()).isEqualTo("Main을 수정했습니다.");

        Prompt secondPrompt = chatModel.receivedPrompts().get(1);
        assertThat(secondPrompt.getInstructions().getLast()).isInstanceOf(ToolResponseMessage.class);
        assertThat(toolCallbacksOf(secondPrompt)).isNotEmpty();
        assertThat(((OpenAiChatOptions) secondPrompt.getOptions()).getPromptCacheKey()).isEqualTo("attempt-1");
    }

    @Test
    void 미존재_경로_edit_file은_에러_문자열을_툴_결과로_전달한다() {
        chatModel.queue(toolCallResponse("edit_file", "{\"path\":\"src/None.java\",\"content\":\"새 파일\"}"));
        chatModel.queue(textResponse("파일을 찾지 못했습니다."));

        GeneratedCode generated = codeGenerator.generate(problem, attempt(), "새 파일 만들어줘");

        ToolResponseMessage toolResponse =
                (ToolResponseMessage) chatModel.receivedPrompts().get(1).getInstructions().getLast();
        assertThat(toolResponse.getResponses().getFirst().responseData()).contains("파일을 찾을 수 없습니다");
        assertThat(generated.files()).containsExactly(new ProblemFile("src/Main.java", "class Main {}"));
        assertThat(generated.toolCalls()).containsExactly(new ToolCallEntry("edit_file", "src/None.java"));
    }

    @Test
    void 열_라운드를_넘으면_툴_없이_마무리_호출을_한다() {
        for (int round = 1; round <= 10; round++) {
            chatModel.queue(toolCallResponse(
                    "edit_file", "{\"path\":\"src/Main.java\",\"content\":\"수정 " + round + "\"}"));
        }
        chatModel.queue(textResponse("한도까지 작업했습니다."));

        GeneratedCode generated = codeGenerator.generate(problem, attempt(), "계속 고쳐줘");

        assertThat(chatModel.receivedPrompts()).hasSize(11);

        for (int round = 0; round < 10; round++) {
            assertThat(toolCallbacksOf(chatModel.receivedPrompts().get(round))).isNotEmpty();
        }

        Prompt finalizePrompt = chatModel.receivedPrompts().get(10);
        assertThat(toolCallbacksOf(finalizePrompt)).isEmpty();
        assertThat(finalizePrompt.getInstructions().getLast().getText()).contains("툴 사용 한도에 도달했습니다");
        assertThat(generated.toolCalls()).hasSize(10);
        assertThat(generated.files()).containsExactly(new ProblemFile("src/Main.java", "수정 10"));
        assertThat(generated.summary()).isEqualTo("한도까지 작업했습니다.");
    }

    @Test
    void 마무리_호출이_실패해도_편집을_보존하고_대체_요약을_반환한다() {
        for (int round = 1; round <= 10; round++) {
            chatModel.queue(toolCallResponse(
                    "edit_file", "{\"path\":\"src/Main.java\",\"content\":\"수정 " + round + "\"}"));
        }
        chatModel.failWith(new IllegalStateException("finalize 실패"));

        GeneratedCode generated = codeGenerator.generate(problem, attempt(), "계속 고쳐줘");

        assertThat(chatModel.receivedPrompts()).hasSize(11);
        assertThat(generated.files()).containsExactly(new ProblemFile("src/Main.java", "수정 10"));
        assertThat(generated.toolCalls()).hasSize(10);
        assertThat(generated.summary()).isEqualTo("작업을 완료했지만 AI가 요약을 제공하지 않았습니다.");
    }

    @Test
    void 최종_텍스트가_비면_대체_요약을_반환한다() {
        chatModel.queue(textResponse(""));

        GeneratedCode generated = codeGenerator.generate(problem, attempt(), "고쳐줘");

        assertThat(generated.summary()).isEqualTo("작업을 완료했지만 AI가 요약을 제공하지 않았습니다.");
    }

    @Test
    void 모델_호출이_실패하면_CodeGenerationException으로_변환한다() {
        chatModel.failWith(new IllegalStateException("provider 오류"));

        assertThatThrownBy(() -> codeGenerator.generate(problem, attempt(), "고쳐줘"))
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("AI 코드 생성 요청에 실패했습니다.");
    }

    private List<?> toolCallbacksOf(Prompt prompt) {
        return ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks();
    }

    private AttemptView attempt() {
        List<ProblemFile> files = List.of(new ProblemFile("src/Main.java", "class Main {}"));

        return new AttemptView(1L, 1L, files, files, List.of(), AttemptStatus.IN_PROGRESS, null);
    }

    private ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).build())));
    }

    private ChatResponse toolCallResponse(String name, String argumentsJson) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-" + name + "-" + argumentsJson.hashCode(), "function", name, argumentsJson)))
                .build();

        return new ChatResponse(List.of(new Generation(message)));
    }

    private static final class StubChatModel implements ChatModel {

        private final Deque<ChatResponse> queued = new ArrayDeque<>();
        private final List<Prompt> receivedPrompts = new ArrayList<>();

        private RuntimeException failure;

        @Override
        public ChatResponse call(Prompt prompt) {
            receivedPrompts.add(prompt);

            if (!queued.isEmpty()) {
                return queued.removeFirst();
            }

            if (failure != null) {
                throw failure;
            }

            throw new IllegalStateException("예상하지 못한 모델 호출입니다.");
        }

        void queue(ChatResponse response) {
            queued.addLast(response);
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        List<Prompt> receivedPrompts() {
            return List.copyOf(receivedPrompts);
        }
    }
}
