package com.promptstudio.ai;

import com.openai.models.completions.CompletionUsage;
import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.port.FeedbackGenerationException;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiFeedbackGeneratorTest {

    private final ProblemView problem = new ProblemView(1L, "제목", "# 명세", List.of());

    private final StubChatModel chatModel = new StubChatModel();

    private final OpenAiFeedbackGenerator feedbackGenerator = new OpenAiFeedbackGenerator(
            ChatClient.builder(chatModel),
            new AiCallExecutor(),
            new OpenAiChatOptionsFactory("code-model", "feedback-model")
    );

    @Test
    void 구조화_응답을_턴별_피드백과_전체_피드백으로_변환한다() {
        chatModel.queue(textResponse("""
                {"turnFeedbacks":["첫 턴 피드백","둘째 턴 피드백"],"overall":"전체 피드백"}
                """));

        AttemptFeedback feedback = feedbackGenerator.generate(problem, attempt(2));

        assertThat(feedback.turnFeedbacks()).containsExactly("첫 턴 피드백", "둘째 턴 피드백");
        assertThat(feedback.overall()).isEqualTo("전체 피드백");
    }

    @Test
    void 요청_옵션에_턴_수만큼의_구조화_출력_스키마를_싣는다() {
        chatModel.queue(textResponse("{\"turnFeedbacks\":[\"첫 턴 피드백\"],\"overall\":\"전체 피드백\"}"));

        feedbackGenerator.generate(problem, attempt(1));

        OpenAiChatOptions options = (OpenAiChatOptions) chatModel.receivedPrompts().getFirst().getOptions();
        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getJsonSchema())
                .contains("turnFeedbacks")
                .contains("정확히 1개")
                .doesNotContain("minItems", "maxItems");
    }

    @Test
    void JSON이_아닌_응답이면_invalid_json으로_실패한다() {
        chatModel.queue(textResponse("피드백을 드릴 수 없습니다."));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("invalid-json");
    }

    @Test
    void 빈_응답이면_empty_content로_실패한다() {
        chatModel.queue(textResponse(""));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("empty-content");
    }

    @Test
    void 턴_피드백_개수가_턴_수와_다르면_turn_count_mismatch로_실패한다() {
        chatModel.queue(textResponse("{\"turnFeedbacks\":[\"첫 턴 피드백\"],\"overall\":\"전체 피드백\"}"));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(2)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("turn-count-mismatch");
    }

    @Test
    void 전체_피드백이_비어_있으면_empty_overall로_실패한다() {
        chatModel.queue(textResponse("{\"turnFeedbacks\":[\"첫 턴 피드백\"],\"overall\":\"  \"}"));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("empty-overall");
    }

    /**
     * 본문만 보면 정상 JSON이라 finish_reason 없이는 잘림을 알 수 없다.
     */
    @Test
    void 완성_토큰_상한에서_잘린_응답이면_truncated로_실패한다() {
        chatModel.queue(truncatedResponse("{\"turnFeedbacks\":[\"첫 턴 피드백\"],\"overall\":\"전체 피드백\"}"));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("truncated");
    }

    @Test
    void 모델_호출이_실패하면_provider_error로_변환한다() {
        chatModel.failWith(new IllegalStateException("provider 오류"));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("provider-error");
    }

    @Test
    void 성공_피드백에_사용량이_실린다() {
        chatModel.queue(withUsage(
                textResponse("{\"turnFeedbacks\":[\"첫 턴 피드백\"],\"overall\":\"전체 피드백\"}"), 500, 120, 300L, 80L));

        AttemptFeedback feedback = feedbackGenerator.generate(problem, attempt(1));

        assertThat(feedback.llmCalls()).hasSize(1);

        LlmCallUsage usage = feedback.llmCalls().getFirst();
        assertThat(usage.seq()).isEqualTo(1);
        assertThat(usage.model()).isEqualTo("feedback-model");
        assertThat(usage.inputTokens()).isEqualTo(500L);
        assertThat(usage.outputTokens()).isEqualTo(120L);
        assertThat(usage.cachedInputTokens()).isEqualTo(300L);
        assertThat(usage.reasoningTokens()).isEqualTo(80L);
        assertThat(usage.latencyMs()).isNotNegative();
    }

    @Test
    void 파싱_실패_예외에도_호출_사용량이_실린다() {
        chatModel.queue(withUsage(textResponse("피드백을 드릴 수 없습니다."), 500, 120, null, null));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOfSatisfying(FeedbackGenerationException.class, exception -> {
                    assertThat(exception.errorType()).isEqualTo("invalid-json");
                    assertThat(exception.llmCalls()).hasSize(1);
                    assertThat(exception.llmCalls().getFirst().inputTokens()).isEqualTo(500L);
                });
    }

    private AttemptView attempt(int turnCount) {
        List<ProblemFile> files = List.of(new ProblemFile("src/Main.java", "class Main {}"));
        List<AttemptView.TurnView> turns = new ArrayList<>();

        for (int index = 1; index <= turnCount; index++) {
            turns.add(
                    new AttemptView.TurnView("프롬프트 " + index, "요약 " + index, List.of(), List.of(), null, null));
        }

        return new AttemptView(1L, 1L, files, files, turns, AttemptStatus.IN_PROGRESS, null, null);
    }

    private ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).build())));
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

    private ChatResponse truncatedResponse(String text) {
        return new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content(text).build(),
                ChatGenerationMetadata.builder().finishReason("length").build()
        )));
    }
}
