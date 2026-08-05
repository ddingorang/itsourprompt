package com.promptstudio.ai;

import com.openai.models.completions.CompletionUsage;
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

    private final OpenAiChatOptionsFactory chatOptionsFactory =
            new OpenAiChatOptionsFactory("code-model", "feedback-model", "scope-model");

    private final OpenAiFeedbackGenerator feedbackGenerator = new OpenAiFeedbackGenerator(
            ChatClient.builder(chatModel),
            new AiCallExecutor(),
            "OPENAI FEEDBACK",
            FeedbackPrompts.systemPrompt(),
            FeedbackPrompts::userPrompt,
            chatOptionsFactory::forFeedback
    );

    @Test
    void 구조화_응답을_턴별_피드백과_전체_피드백으로_변환한다() {
        chatModel.queue(textResponse(response("첫 턴 피드백", "둘째 턴 피드백")));

        FeedbackDraft feedback = feedbackGenerator.generate(problem, attempt(2));

        assertThat(feedback.turnFeedbacks()).containsExactly("첫 턴 피드백", "둘째 턴 피드백");
        assertThat(feedback.overall()).isEqualTo("전체 피드백");
    }

    /**
     * 인용은 응답이 입력에 붙어 있는지 재는 계량기라 대조를 마치면 버린다. 저장 봉투는 그대로다 —
     * 인용이 draft로 새면 화면과 DB에 근거 문장이 그대로 나간다.
     */
    @Test
    void 근거_인용은_턴_피드백에_남기지_않는다() {
        chatModel.queue(textResponse("""
                {"turnFeedbacks":[{"quotes":["프롬프트 1","요약 1"],"feedback":"첫 턴 피드백"}],\
                "overall":"전체 피드백"}"""));

        FeedbackDraft feedback = feedbackGenerator.generate(problem, attempt(1));

        assertThat(feedback.turnFeedbacks()).containsExactly("첫 턴 피드백");
        assertThat(feedback.overall()).doesNotContain("프롬프트 1");
    }

    @Test
    void 요청_옵션에_턴_수만큼의_구조화_출력_스키마를_싣는다() {
        chatModel.queue(textResponse(response("첫 턴 피드백")));

        feedbackGenerator.generate(problem, attempt(1));

        OpenAiChatOptions options = (OpenAiChatOptions) chatModel.receivedPrompts().getFirst().getOptions();
        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getJsonSchema())
                .contains("turnFeedbacks")
                .contains("정확히 1개")
                .contains("\"minItems\": 1")
                .contains("\"maxItems\": 1")
                .contains("\"quotes\"")
                .contains("\"required\": [\"quotes\", \"feedback\"]");
    }

    @Test
    void JSON이_아닌_응답이면_invalid_json으로_실패한다() {
        chatModel.queue(textResponse("피드백을 드릴 수 없습니다."));
        chatModel.queue(textResponse("피드백을 드릴 수 없습니다."));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("invalid-json");
    }

    @Test
    void 빈_응답이면_empty_content로_실패한다() {
        chatModel.queue(textResponse(""));
        chatModel.queue(textResponse(""));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("empty-content");
    }

    @Test
    void 턴_피드백_개수가_턴_수와_다르면_turn_count_mismatch로_실패한다() {
        chatModel.queue(textResponse(response("첫 턴 피드백")));
        chatModel.queue(textResponse(response("첫 턴 피드백")));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(2)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("turn-count-mismatch");
    }

    @Test
    void 전체_피드백이_비어_있으면_empty_overall로_실패한다() {
        chatModel.queue(textResponse(responseWithOverall("  ", "첫 턴 피드백")));
        chatModel.queue(textResponse(responseWithOverall("  ", "첫 턴 피드백")));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("empty-overall");
    }

    /**
     * 본문만 보면 정상 JSON이라 finish_reason 없이는 잘림을 알 수 없다.
     *
     * <p>잘림은 완성 토큰 예산이 모자란 것이라 같은 옵션으로 다시 불러도 같은 자리에서 잘린다 — 재호출하지 않는다.
     */
    @Test
    void 완성_토큰_상한에서_잘린_응답이면_다시_부르지_않고_truncated로_실패한다() {
        chatModel.queue(truncatedResponse(response("첫 턴 피드백")));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("truncated");
        assertThat(chatModel.receivedPrompts()).hasSize(1);
    }

    /**
     * 응답 형태가 어긋나는 실패는 같은 요청을 다시 보내면 통과하는 경우가 많다. 사용자가 손으로 하던
     * 재제출을 어댑터가 대신한다.
     */
    @Test
    void 응답_형태가_어긋나면_한_번_다시_호출한다() {
        chatModel.queue(textResponse(response("첫 턴 피드백")));
        chatModel.queue(textResponse(response("첫 턴", "둘째 턴")));

        FeedbackDraft feedback = feedbackGenerator.generate(problem, attempt(2));

        assertThat(feedback.turnFeedbacks()).containsExactly("첫 턴", "둘째 턴");
        assertThat(chatModel.receivedPrompts()).hasSize(2);
    }

    /**
     * 재시도는 한 번뿐이다. 제출 하나가 AI 호출을 무한정 늘리지 않게 상한을 못 박는다.
     */
    @Test
    void 다시_호출한_응답도_어긋나면_두_번째에서_멈춘다() {
        chatModel.queue(textResponse(response("첫 턴 피드백")));
        chatModel.queue(textResponse(response("첫 턴 피드백")));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(2)))
                .isInstanceOf(FeedbackGenerationException.class);
        assertThat(chatModel.receivedPrompts()).hasSize(2);
    }

    /**
     * 프로바이더 실패는 이미 spring.ai의 HTTP 재시도를 거쳤고 400처럼 확정된 실패도 섞여 있어 다시 부르지 않는다.
     */
    @Test
    void 모델_호출이_실패하면_다시_부르지_않고_provider_error로_변환한다() {
        chatModel.failWith(new IllegalStateException("provider 오류"));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .extracting("reason")
                .isEqualTo("provider-error");
        assertThat(chatModel.receivedPrompts()).hasSize(1);
    }

    @Test
    void 성공_피드백에_사용량이_실린다() {
        chatModel.queue(withUsage(textResponse(response("첫 턴 피드백")), 500, 120, 300L, 80L));

        FeedbackDraft feedback = feedbackGenerator.generate(problem, attempt(1));

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

    /**
     * 재호출까지 실패해도 두 호출이 쓴 토큰은 모두 예외에 실린다.
     */
    @Test
    void 파싱_실패_예외에도_호출_사용량이_실린다() {
        chatModel.queue(withUsage(textResponse("피드백을 드릴 수 없습니다."), 500, 120, null, null));
        chatModel.queue(withUsage(textResponse("피드백을 드릴 수 없습니다."), 500, 120, null, null));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOfSatisfying(FeedbackGenerationException.class, exception -> {
                    assertThat(exception.errorType()).isEqualTo("invalid-json");
                    assertThat(exception.llmCalls()).hasSize(2);
                    assertThat(exception.llmCalls().getFirst().inputTokens()).isEqualTo(500L);
                });
    }

    /**
     * 턴 항목이 인용 배열과 피드백 문자열을 함께 갖는 새 응답 모양. 인용은 대조용이라 여기서는 비워 둔다.
     */
    private String response(String... turnFeedbacks) {
        return responseWithOverall("전체 피드백", turnFeedbacks);
    }

    private String responseWithOverall(String overall, String... turnFeedbacks) {
        StringBuilder entries = new StringBuilder();

        for (String turnFeedback : turnFeedbacks) {
            if (!entries.isEmpty()) {
                entries.append(",");
            }

            entries.append("{\"quotes\":[],\"feedback\":\"").append(turnFeedback).append("\"}");
        }

        return "{\"turnFeedbacks\":[" + entries + "],\"overall\":\"" + overall + "\"}";
    }

    private AttemptView attempt(int turnCount) {
        List<ProblemFile> files = List.of(new ProblemFile("src/Main.java", "class Main {}"));
        List<AttemptView.TurnView> turns = new ArrayList<>();

        for (int index = 1; index <= turnCount; index++) {
            turns.add(
                    new AttemptView.TurnView("프롬프트 " + index, "요약 " + index, List.of(), List.of(), null, null, null));
        }

        return new AttemptView(1L, 1L, files, files, turns, AttemptStatus.IN_PROGRESS, null, null, null);
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
