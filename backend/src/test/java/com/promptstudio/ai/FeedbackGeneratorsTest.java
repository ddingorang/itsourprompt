package com.promptstudio.ai;

import com.openai.models.completions.CompletionUsage;
import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.port.FeedbackGenerationException;
import com.promptstudio.attempt.port.FeedbackTimeoutException;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackGeneratorsTest {

    /** 두 호출은 시스템 프롬프트로만 갈린다 — 순서로 가르면 어느 브랜치가 어느 응답을 집는지가 운이다. */
    private static final String PROMPT_LENS = "prompt-writing coach";
    private static final String PATTERN_LENS = "AI-coding coach";

    private final ProblemView problem = new ProblemView(1L, "제목", "# 명세", List.of());

    private final StubChatModel chatModel = new StubChatModel();
    private final AiCallExecutor aiCallExecutor = new AiCallExecutor();

    private final FeedbackGenerators feedbackGenerators = new FeedbackGenerators(
            ChatClient.builder(chatModel),
            aiCallExecutor,
            new OpenAiChatOptionsFactory("code-model", "feedback-model", "scope-model")
    );

    @Test
    void 두_렌즈의_피드백을_한_봉투에_합친다() {
        chatModel.queueFor(PROMPT_LENS, textResponse(response("프롬프트 총평", "턴 1 프롬프트")));
        chatModel.queueFor(PATTERN_LENS, textResponse(response("패턴 총평", "턴 1 패턴")));

        AttemptFeedback feedback = feedbackGenerators.generate(problem, attempt(1));

        assertThat(feedback.turnFeedbacks()).containsExactly("턴 1 프롬프트");
        assertThat(feedback.overall()).isEqualTo("프롬프트 총평");
        assertThat(feedback.patternTurnFeedbacks()).containsExactly("턴 1 패턴");
    }

    /**
     * 출처는 모델에게 맡기지 않고 BE가 붙인다.
     */
    @Test
    void 패턴_총평_뒤에_출처_한_줄을_붙인다() {
        chatModel.queueFor(PROMPT_LENS, textResponse(response("프롬프트 총평", "턴 1 프롬프트")));
        chatModel.queueFor(PATTERN_LENS, textResponse(response("패턴 총평", "턴 1 패턴")));

        AttemptFeedback feedback = feedbackGenerators.generate(problem, attempt(1));

        assertThat(feedback.patternOverall()).isEqualTo("패턴 총평" + PatternPrompts.SOURCE_NOTE);
    }

    /**
     * purpose가 갈려 따로 저장되므로 사용량도 렌즈별로 나뉘어 나온다.
     */
    @Test
    void 두_렌즈의_사용량을_따로_싣는다() {
        chatModel.queueFor(PROMPT_LENS, withUsage(
                textResponse(response("프롬프트 총평", "턴 1 프롬프트")), 500, 120));
        chatModel.queueFor(PATTERN_LENS, withUsage(
                textResponse(response("패턴 총평", "턴 1 패턴")), 300, 60));

        AttemptFeedback feedback = feedbackGenerators.generate(problem, attempt(1));

        assertThat(feedback.llmCalls()).extracting(LlmCallUsage::inputTokens).containsExactly(500L);
        assertThat(feedback.patternLlmCalls()).extracting(LlmCallUsage::inputTokens).containsExactly(300L);
    }

    /**
     * 호출 수만 세면 두 브랜치가 같은 시스템 프롬프트를 보내도 통과한다 — 각 호출이 자기 렌즈의 것을
     * 보냈는지 본다. 두 호출이 병렬이라 도착 순서는 정해져 있지 않다.
     */
    @Test
    void 두_렌즈에_각각_자기_시스템_프롬프트를_보낸다() {
        chatModel.queueFor(PROMPT_LENS, textResponse(response("프롬프트 총평", "턴 1 프롬프트")));
        chatModel.queueFor(PATTERN_LENS, textResponse(response("패턴 총평", "턴 1 패턴")));

        feedbackGenerators.generate(problem, attempt(1));

        assertThat(chatModel.receivedPrompts())
                .hasSize(2)
                .extracting(StubChatModel::systemTextOf)
                .satisfiesExactlyInAnyOrder(
                        systemText -> assertThat(systemText).contains(PROMPT_LENS).doesNotContain(PATTERN_LENS),
                        systemText -> assertThat(systemText).contains(PATTERN_LENS).doesNotContain(PROMPT_LENS));
    }

    /**
     * 실패해도 이미 끝난 호출의 토큰은 과금된다 — 실패한 쪽뿐 아니라 성공한 쪽의 사용량도 함께 실어야
     * 기록이 맞는다. 성공한 쪽 사용량이 실렸다는 것은 그 브랜치를 끝까지 기다렸다는 뜻이기도 하다.
     */
    @Test
    void 한쪽이_실패하면_제출이_실패하고_양쪽_사용량이_예외에_실린다() {
        chatModel.queueFor(PROMPT_LENS, withUsage(textResponse("피드백을 드릴 수 없습니다."), 500, 120));
        chatModel.queueFor(PROMPT_LENS, withUsage(textResponse("피드백을 드릴 수 없습니다."), 500, 120));
        chatModel.queueFor(PATTERN_LENS, withUsage(
                textResponse(response("패턴 총평", "턴 1 패턴")), 300, 60));

        assertThatThrownBy(() -> feedbackGenerators.generate(problem, attempt(1)))
                .isInstanceOfSatisfying(FeedbackGenerationException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo("invalid-json");
                    assertThat(exception.llmCalls())
                            .extracting(LlmCallUsage::seq, LlmCallUsage::inputTokens)
                            .containsExactly(
                                    tuple(1, 500L),
                                    tuple(2, 500L),
                                    tuple(3, 300L));
                });
    }

    /**
     * 재호출은 각 생성기 안에서 이미 끝났다 — 합친 예외를 재시도 대상 표식으로 던지면 한 번 더 돈다.
     */
    @Test
    void 합친_실패는_재시도_대상_표식으로_던지지_않는다() {
        chatModel.queueFor(PROMPT_LENS, textResponse("피드백을 드릴 수 없습니다."));
        chatModel.queueFor(PROMPT_LENS, textResponse("피드백을 드릴 수 없습니다."));
        chatModel.queueFor(PATTERN_LENS, textResponse(response("패턴 총평", "턴 1 패턴")));

        assertThatThrownBy(() -> feedbackGenerators.generate(problem, attempt(1)))
                .isInstanceOf(FeedbackGenerationException.class)
                .isNotInstanceOf(FeedbackResponseException.class);
    }

    /**
     * 사용자가 실제로 겪은 것은 5분을 기다린 일이다. 다른 실패가 섞여도 504로 나가야 한다.
     */
    @Test
    void 타임아웃이_섞이면_타임아웃이_이긴다() {
        BranchFailingExecutor executor = new BranchFailingExecutor(
                new FeedbackTimeoutException(List.of(usage(1, 100L))),
                new FeedbackGenerationException("invalid-json", "깨진 응답", null, List.of(usage(1, 200L)))
        );
        FeedbackGenerators generators = new FeedbackGenerators(
                ChatClient.builder(chatModel),
                executor,
                new OpenAiChatOptionsFactory("code-model", "feedback-model", "scope-model")
        );

        assertThatThrownBy(() -> generators.generate(problem, attempt(1)))
                .isInstanceOfSatisfying(FeedbackTimeoutException.class, exception ->
                        assertThat(exception.llmCalls())
                                .extracting(LlmCallUsage::seq, LlmCallUsage::inputTokens)
                                .containsExactly(
                                        tuple(1, 100L),
                                        tuple(2, 200L)));
    }

    /**
     * 5분 상한을 실제로 기다리지 않고 브랜치 실패를 만드는 손 stub. 브랜치 순서는 generate가
     * prompt → pattern으로 고정하므로 어느 실패가 어느 렌즈로 가는지가 정해져 있다.
     */
    private static final class BranchFailingExecutor extends AiCallExecutor {

        private final Queue<RuntimeException> failures;

        private BranchFailingExecutor(RuntimeException... failures) {
            this.failures = new ArrayDeque<>(List.of(failures));
        }

        @Override
        public <T> Future<T> submit(Callable<T> aiCall) {
            RuntimeException failure = failures.poll();

            if (failure == null) {
                return super.submit(aiCall);
            }

            return super.submit(() -> {
                throw failure;
            });
        }
    }

    /**
     * 턴 항목이 인용 배열과 피드백 문자열을 함께 갖는 새 응답 모양. 인용은 대조용이라 여기서는 비워 둔다.
     */
    private String response(String overall, String... turnFeedbacks) {
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
            turns.add(new AttemptView.TurnView(
                    "프롬프트 " + index, "요약 " + index, List.of(), List.of(), null, null, null));
        }

        return new AttemptView(1L, 1L, files, files, turns, AttemptStatus.IN_PROGRESS, null, null, null);
    }

    private LlmCallUsage usage(int seq, long inputTokens) {
        return new LlmCallUsage(seq, "feedback-model", inputTokens, 10L, null, null, 5L);
    }

    private ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).build())));
    }

    private ChatResponse withUsage(ChatResponse response, long input, long output) {
        Usage usage = new DefaultUsage(
                (int) input,
                (int) output,
                (int) (input + output),
                CompletionUsage.builder()
                        .promptTokens(input)
                        .completionTokens(output)
                        .totalTokens(input + output)
                        .build(),
                null,
                null
        );

        return new ChatResponse(response.getResults(), ChatResponseMetadata.builder().usage(usage).build());
    }
}
