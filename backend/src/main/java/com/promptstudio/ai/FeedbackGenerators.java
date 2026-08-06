package com.promptstudio.ai;

import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.domain.TurnTestResults;
import com.promptstudio.attempt.port.FeedbackGenerationException;
import com.promptstudio.attempt.port.FeedbackGenerator;
import com.promptstudio.attempt.port.FeedbackTimeoutException;
import com.promptstudio.attempt.port.LlmUsageCarrier;
import com.promptstudio.problem.domain.ProblemView;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * 같은 세션을 두 렌즈로 동시에 굽고 하나의 피드백으로 합친다. 포트를 구현하는 것은 이 클래스 하나뿐이다.
 *
 * <p>두 렌즈 모두 필수다 — 한쪽만 나온 화면은 만들지 않는다. 대가는 제출 실패율이 대략 두 배가 되는 것이다.
 */
@Component
public class FeedbackGenerators implements FeedbackGenerator {

    private final AiCallExecutor aiCallExecutor;
    private final OpenAiFeedbackGenerator prompt;
    private final OpenAiFeedbackGenerator pattern;

    public FeedbackGenerators(
            ChatClient.Builder chatClientBuilder,
            AiCallExecutor aiCallExecutor,
            OpenAiChatOptionsFactory chatOptionsFactory
    ) {
        this.aiCallExecutor = aiCallExecutor;
        this.prompt = new OpenAiFeedbackGenerator(
                chatClientBuilder,
                aiCallExecutor,
                "OPENAI FEEDBACK",
                FeedbackPrompts.systemPrompt(),
                FeedbackPrompts::userPrompt,
                chatOptionsFactory::forFeedback
        );
        this.pattern = new OpenAiFeedbackGenerator(
                chatClientBuilder,
                aiCallExecutor,
                "OPENAI PATTERN",
                PatternPrompts.systemPrompt(),
                (problem, attempt, testResults) -> PatternPrompts.userPrompt(problem, attempt),
                chatOptionsFactory::forPatternFeedback,
                PatternPrompts::renderTurn
        );
    }

    @Override
    public AttemptFeedback generate(ProblemView problem, AttemptView attempt, TurnTestResults testResults) {
        Future<FeedbackDraft> promptCall =
                aiCallExecutor.submit(() -> prompt.generate(problem, attempt, testResults));
        Future<FeedbackDraft> patternCall =
                aiCallExecutor.submit(() -> pattern.generate(problem, attempt, testResults));

        Settled promptResult = await(promptCall);
        Settled patternResult = await(patternCall);

        if (promptResult.failed() || patternResult.failed()) {
            throw merged(promptResult, patternResult);
        }

        return new AttemptFeedback(
                promptResult.draft().turnFeedbacks(),
                promptResult.draft().overall(),
                patternResult.draft().turnFeedbacks(),
                patternResult.draft().overall() + PatternPrompts.SOURCE_NOTE,
                promptResult.draft().llmCalls(),
                patternResult.draft().llmCalls()
        );
    }

    /**
     * 한쪽이 실패해도 반대쪽을 끝까지 기다린다.
     *
     * <p>바깥에 타임아웃을 두지 않는다. 상한은 각 생성기 안의 {@code AiCallExecutor.call(..., 5분)}이 이미
     * 갖고 있고, 바깥에서 또 재면 자기 예산 안에서 정상 진행 중인 브랜치를 자른다. 그러면 그 브랜치의
     * 사용량 tracker가 들고 있던 토큰이 통째로 사라진다.
     *
     * <p>실패해도 반대쪽을 취소하지 않는다. {@code future.cancel(true)}는 바깥 작업 스레드만 인터럽트하고,
     * 그 스레드가 블로킹 중인 안쪽 {@code call}은 InterruptedException에서 자기 future를 취소하지 않는다 —
     * 즉 AI 호출은 죽지 않고 토큰만 과금된 채 결과를 아무도 읽지 않는다. 취소는 두 번 거짓말한다.
     */
    private Settled await(Future<FeedbackDraft> call) {
        try {
            return new Settled(call.get(), null);
        } catch (ExecutionException exception) {
            return new Settled(null, asFailure(exception.getCause()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return new Settled(null, new FeedbackGenerationException(
                    FeedbackGenerationException.INTERRUPTED, "AI feedback request was interrupted.", exception));
        }
    }

    /**
     * 생성기가 던지는 것은 {@link FeedbackTimeoutException} 아니면 {@link FeedbackGenerationException}이다.
     * 그 밖의 실패만 감싸고, 둘은 타입 그대로 들고 있어야 {@link #merged}가 504와 502를 가른다.
     */
    private RuntimeException asFailure(Throwable cause) {
        if (cause instanceof FeedbackTimeoutException || cause instanceof FeedbackGenerationException) {
            return (RuntimeException) cause;
        }

        return new FeedbackGenerationException(
                FeedbackGenerationException.PROVIDER_ERROR, "AI feedback request failed.", cause, List.of());
    }

    /**
     * 두 브랜치의 사용량을 합쳐 seq를 1..N으로 다시 매기고 하나의 예외에 싣는다. 실패해도 이미 끝난 호출의
     * 토큰은 과금되므로, 성공한 쪽의 사용량도 함께 실어야 기록이 맞는다.
     *
     * <p>실패 경로는 purpose를 나누지 않는다 — 어느 렌즈가 무너졌는지는 이미 로그에 있다.
     */
    private RuntimeException merged(Settled promptResult, Settled patternResult) {
        List<LlmCallUsage> calls = renumbered(usageOf(promptResult), usageOf(patternResult));

        // 사용자가 실제로 겪은 것은 5분을 기다린 일이므로, 타임아웃이 섞이면 타임아웃(504)이 이긴다.
        if (promptResult.failure() instanceof FeedbackTimeoutException
                || patternResult.failure() instanceof FeedbackTimeoutException) {
            return new FeedbackTimeoutException(calls);
        }

        RuntimeException failure = promptResult.failed() ? promptResult.failure() : patternResult.failure();

        // FeedbackResponseException(재시도 대상 표식)으로는 던지지 않는다 — 재호출은 각 생성기 안에서 끝났다.
        if (failure instanceof FeedbackGenerationException generationFailure) {
            return new FeedbackGenerationException(
                    generationFailure.reason(), failure.getMessage(), failure.getCause(), calls);
        }

        return new FeedbackGenerationException(
                FeedbackGenerationException.PROVIDER_ERROR, failure.getMessage(), failure, calls);
    }

    private List<LlmCallUsage> usageOf(Settled settled) {
        if (!settled.failed()) {
            return settled.draft().llmCalls();
        }

        return settled.failure() instanceof LlmUsageCarrier carrier ? carrier.llmCalls() : List.of();
    }

    private List<LlmCallUsage> renumbered(List<LlmCallUsage> first, List<LlmCallUsage> second) {
        List<LlmCallUsage> calls = new ArrayList<>();

        for (LlmCallUsage usage : first) {
            calls.add(usage.withSeq(calls.size() + 1));
        }

        for (LlmCallUsage usage : second) {
            calls.add(usage.withSeq(calls.size() + 1));
        }

        return calls;
    }

    /**
     * 브랜치 하나가 끝난 자리. 실패해도 반대쪽을 기다려야 하므로 예외를 바로 던지지 않고 여기 담아 둔다.
     */
    private record Settled(FeedbackDraft draft, RuntimeException failure) {

        boolean failed() {
            return failure != null;
        }
    }
}
