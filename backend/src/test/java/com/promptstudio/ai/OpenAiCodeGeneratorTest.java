package com.promptstudio.ai;

import com.openai.models.completions.CompletionUsage;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.attempt.port.CodeGenerationException;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.ArrayList;
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
            new OpenAiChatOptionsFactory("code-model", "feedback-model", "scope-model")
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

    @Test
    void 라운드마다_토큰_사용량을_기록한다() {
        chatModel.queue(withUsage(
                toolCallResponse("edit_file", "{\"path\":\"src/Main.java\",\"content\":\"수정\"}"), 100, 40, 30L, 10L));
        chatModel.queue(withUsage(textResponse("고쳤습니다."), 200, 60, 50L, 20L));

        GeneratedCode generated = codeGenerator.generate(problem, attempt(), "고쳐줘");

        assertThat(generated.llmCalls()).hasSize(2);

        LlmCallUsage first = generated.llmCalls().getFirst();
        assertThat(first.seq()).isEqualTo(1);
        assertThat(first.model()).isEqualTo("code-model");
        assertThat(first.inputTokens()).isEqualTo(100L);
        assertThat(first.outputTokens()).isEqualTo(40L);
        assertThat(first.cachedInputTokens()).isEqualTo(30L);
        assertThat(first.reasoningTokens()).isEqualTo(10L);
        assertThat(first.latencyMs()).isNotNegative();

        LlmCallUsage second = generated.llmCalls().get(1);
        assertThat(second.seq()).isEqualTo(2);
        assertThat(second.inputTokens()).isEqualTo(200L);
        assertThat(second.outputTokens()).isEqualTo(60L);
    }

    @Test
    void 마무리_호출도_사용량에_포함된다() {
        for (int round = 1; round <= 10; round++) {
            chatModel.queue(withUsage(toolCallResponse(
                    "edit_file", "{\"path\":\"src/Main.java\",\"content\":\"수정 " + round + "\"}"), 10, 5, null, null));
        }
        chatModel.queue(withUsage(textResponse("한도까지 작업했습니다."), 20, 7, null, null));

        GeneratedCode generated = codeGenerator.generate(problem, attempt(), "계속 고쳐줘");

        assertThat(generated.llmCalls()).hasSize(11);
        assertThat(generated.llmCalls()).extracting(LlmCallUsage::seq)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        assertThat(generated.llmCalls().getLast().inputTokens()).isEqualTo(20L);
    }

    @Test
    void 모델_호출_실패_예외에_누적_사용량이_실린다() {
        chatModel.queue(withUsage(
                toolCallResponse("edit_file", "{\"path\":\"src/Main.java\",\"content\":\"수정\"}"), 100, 40, null, null));
        chatModel.failWith(new IllegalStateException("provider 오류"));

        assertThatThrownBy(() -> codeGenerator.generate(problem, attempt(), "고쳐줘"))
                .isInstanceOfSatisfying(CodeGenerationException.class, exception -> {
                    assertThat(exception.errorType()).isEqualTo("provider-error");
                    assertThat(exception.llmCalls()).hasSize(1);
                    assertThat(exception.llmCalls().getFirst().inputTokens()).isEqualTo(100L);
                });
    }

    private List<?> toolCallbacksOf(Prompt prompt) {
        return ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks();
    }

    private AttemptView attempt() {
        List<ProblemFile> files = List.of(new ProblemFile("src/Main.java", "class Main {}"));

        return new AttemptView(1L, 1L, files, files, List.of(), AttemptStatus.IN_PROGRESS, null, null, null);
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

    /**
     * 실제 응답처럼 usage 메타데이터를 실은 응답으로 바꾼다. reasoning은 OpenAI native usage에만 담긴다.
     */
    private ChatResponse withUsage(ChatResponse response, long input, long output, Long cached, Long reasoning) {
        return new ChatResponse(
                response.getResults(),
                ChatResponseMetadata.builder().usage(usage(input, output, cached, reasoning)).build()
        );
    }

    private Usage usage(long input, long output, Long cached, Long reasoning) {
        CompletionUsage.Builder nativeUsage = CompletionUsage.builder()
                .promptTokens(input)
                .completionTokens(output)
                .totalTokens(input + output);

        if (reasoning != null) {
            nativeUsage.completionTokensDetails(
                    CompletionUsage.CompletionTokensDetails.builder().reasoningTokens(reasoning).build());
        }

        return new DefaultUsage(
                (int) input, (int) output, (int) (input + output), nativeUsage.build(), cached, null);
    }
}
