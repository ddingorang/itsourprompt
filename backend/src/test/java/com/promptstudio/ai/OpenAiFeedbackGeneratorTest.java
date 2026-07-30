package com.promptstudio.ai;

import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.port.FeedbackGenerationException;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
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
    void JSON이_아닌_응답이면_예외를_던진다() {
        chatModel.queue(textResponse("피드백을 드릴 수 없습니다."));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class);
    }

    @Test
    void 빈_응답이면_예외를_던진다() {
        chatModel.queue(textResponse(""));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class);
    }

    @Test
    void 턴_피드백_개수가_턴_수와_다르면_예외를_던진다() {
        chatModel.queue(textResponse("{\"turnFeedbacks\":[\"첫 턴 피드백\"],\"overall\":\"전체 피드백\"}"));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(2)))
                .isInstanceOf(FeedbackGenerationException.class);
    }

    @Test
    void 전체_피드백이_비어_있으면_예외를_던진다() {
        chatModel.queue(textResponse("{\"turnFeedbacks\":[\"첫 턴 피드백\"],\"overall\":\"  \"}"));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class);
    }

    @Test
    void 모델_호출이_실패하면_FeedbackGenerationException으로_변환한다() {
        chatModel.failWith(new IllegalStateException("provider 오류"));

        assertThatThrownBy(() -> feedbackGenerator.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class);
    }

    private AttemptView attempt(int turnCount) {
        List<ProblemFile> files = List.of(new ProblemFile("src/Main.java", "class Main {}"));
        List<AttemptView.TurnView> turns = new ArrayList<>();

        for (int index = 1; index <= turnCount; index++) {
            turns.add(new AttemptView.TurnView("프롬프트 " + index, "요약 " + index, List.of(), List.of(), null));
        }

        return new AttemptView(1L, 1L, files, files, turns, AttemptStatus.IN_PROGRESS, null);
    }

    private ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).build())));
    }
}
