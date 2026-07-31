package com.promptstudio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.port.FeedbackGenerator;
import com.promptstudio.problem.domain.ProblemView;
import com.promptstudio.attempt.port.FeedbackGenerationException;
import com.promptstudio.attempt.port.FeedbackTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OpenAiFeedbackGenerator implements FeedbackGenerator {

    private static final long FEEDBACK_TIMEOUT_MINUTES = 5;
    private static final String TRUNCATED_FINISH_REASON = "length";

    /**
     * 같은 요청을 다시 보내면 통과하는 경우가 많은 실패들. 이 실패만 재호출 대상으로 삼는다.
     *
     * <p>잘림({@link FeedbackGenerationException#TRUNCATED})은 완성 토큰 예산이 모자란 것이라 같은 옵션으로
     * 다시 불러도 같은 자리에서 잘린다 — 호출 비용만 두 배가 되므로 뺀다.
     */
    private static final Set<String> RETRYABLE_REASONS = Set.of(
            FeedbackGenerationException.EMPTY_CONTENT,
            FeedbackGenerationException.INVALID_JSON,
            FeedbackGenerationException.TURN_COUNT_MISMATCH,
            FeedbackGenerationException.EMPTY_OVERALL
    );

    private static final Logger log = LoggerFactory.getLogger(OpenAiFeedbackGenerator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;
    private final AiCallExecutor aiCallExecutor;
    private final OpenAiChatOptionsFactory chatOptionsFactory;

    public OpenAiFeedbackGenerator(
            ChatClient.Builder chatClientBuilder,
            AiCallExecutor aiCallExecutor,
            OpenAiChatOptionsFactory chatOptionsFactory
    ) {
        this.chatClient = chatClientBuilder.build();
        this.aiCallExecutor = aiCallExecutor;
        this.chatOptionsFactory = chatOptionsFactory;
    }

    private record FeedbackPayload(List<String> turnFeedbacks, String overall) {
    }

    /**
     * 실패 로그에 함께 남기는 호출 정보. 완성 토큰 상한에 걸린 잘림은 finish_reason으로만 알 수 있다.
     */
    private record CallTrace(
            Long attemptId,
            int turnCount,
            Integer maxCompletionTokens,
            String finishReason,
            Integer completionTokens
    ) {

        boolean truncated() {
            return TRUNCATED_FINISH_REASON.equalsIgnoreCase(finishReason);
        }
    }

    @Override
    public AttemptFeedback generate(ProblemView problem, AttemptView attempt) {
        String systemPrompt = FeedbackPrompts.systemPrompt();
        String userPrompt = FeedbackPrompts.userPrompt(problem, attempt);
        int turnCount = attempt.turns().size();
        OpenAiChatOptions chatOptions = chatOptionsFactory.forFeedback(turnCount);

        log.info(
                "[OPENAI FEEDBACK] request started | model={} | problemId={} | attemptId={} | inputChars={} | turns={}",
                chatOptions.getModel(),
                problem.id(),
                attempt.id(),
                systemPrompt.length() + userPrompt.length(),
                turnCount
        );

        LlmUsageTracker tracker = new LlmUsageTracker();

        try {
            return callAndParse(systemPrompt, userPrompt, chatOptions, attempt.id(), turnCount, tracker);
        } catch (FeedbackResponseException exception) {
            log.warn(
                    "[OPENAI FEEDBACK] response contract broken, retrying once | attemptId={} | turns={} | reason={}",
                    attempt.id(),
                    turnCount,
                    exception.reason()
            );

            return callAndParse(systemPrompt, userPrompt, chatOptions, attempt.id(), turnCount, tracker);
        }
    }

    /**
     * 응답 형태가 어긋나는 실패는 같은 요청을 다시 보내면 통과하는 경우가 많아 {@link #generate}가 이 호출을
     * 한 번 더 시도한다. 재시도 자체를 여기 두지 않는 이유는 상한을 한 번으로 못 박기 위해서다.
     *
     * <p>사용량 tracker는 호출 사이에 이어져야 한다 — 재시도로 끝난 제출도 두 호출이 쓴 토큰을 모두 싣는다.
     */
    private AttemptFeedback callAndParse(
            String systemPrompt,
            String userPrompt,
            OpenAiChatOptions chatOptions,
            Long attemptId,
            int turnCount,
            LlmUsageTracker tracker
    ) {
        long startedAt = System.nanoTime();

        try {
            ChatResponse response = aiCallExecutor.call(() -> chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(chatOptions.mutate())
                    .call()
                    .chatResponse(), FEEDBACK_TIMEOUT_MINUTES);

            tracker.record(chatOptions.getModel(), response, elapsedMillis(startedAt));

            CallTrace trace = traceOf(attemptId, turnCount, chatOptions, response);
            String content = contentOf(response);
            AttemptFeedback feedback = parse(content, trace, tracker.snapshot());

            log.info(
                    "[OPENAI FEEDBACK] response received | duration={} ms | finishReason={} | completionTokens={}"
                            + " | feedbackChars={}",
                    elapsedMillis(startedAt),
                    trace.finishReason(),
                    trace.completionTokens(),
                    content.length()
            );
            return feedback;
        } catch (TimeoutException exception) {
            log.error(
                    "[OPENAI FEEDBACK] timed out | duration={} ms | timeout={} min",
                    elapsedMillis(startedAt),
                    FEEDBACK_TIMEOUT_MINUTES
            );
            throw new FeedbackTimeoutException(tracker.snapshot());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FeedbackGenerationException(
                    FeedbackGenerationException.INTERRUPTED,
                    "AI feedback request was interrupted.",
                    exception,
                    tracker.snapshot()
            );
        } catch (ExecutionException exception) {
            log.error(
                    "[OPENAI FEEDBACK] generation failed | reason={} | attemptId={} | turns={} | duration={} ms",
                    FeedbackGenerationException.PROVIDER_ERROR,
                    attemptId,
                    turnCount,
                    elapsedMillis(startedAt),
                    exception.getCause()
            );
            throw new FeedbackGenerationException(
                    FeedbackGenerationException.PROVIDER_ERROR,
                    "AI feedback request failed.",
                    exception.getCause(),
                    tracker.snapshot()
            );
        }
    }

    /**
     * 턴과 피드백의 1:1 대응이 응답의 유일한 계약이므로 개수가 어긋나면 저장하지 않는다.
     *
     * <p>잘린 응답은 본문만 보면 정상 JSON일 수 있어 먼저 걸러낸다.
     *
     * <p>여기까지 왔다면 호출은 이미 성공해 토큰을 썼다 — 실패로 끝나도 그 사용량을 예외에 실어 보낸다.
     */
    private AttemptFeedback parse(String content, CallTrace trace, List<LlmCallUsage> llmCalls) {
        if (trace.truncated()) {
            throw failure(
                    FeedbackGenerationException.TRUNCATED,
                    "AI feedback response was cut off at the completion token limit.",
                    content,
                    trace,
                    llmCalls
            );
        }

        if (content == null || content.isBlank()) {
            throw failure(
                    FeedbackGenerationException.EMPTY_CONTENT,
                    "AI returned an empty feedback response.",
                    content,
                    trace,
                    llmCalls
            );
        }

        FeedbackPayload payload;

        try {
            payload = objectMapper.readValue(content, FeedbackPayload.class);
        } catch (JsonProcessingException exception) {
            throw failure(
                    FeedbackGenerationException.INVALID_JSON,
                    "AI feedback response was not valid JSON.",
                    content,
                    trace,
                    llmCalls,
                    exception
            );
        }

        List<String> turnFeedbacks = payload.turnFeedbacks();

        if (turnFeedbacks == null || turnFeedbacks.size() != trace.turnCount()) {
            throw failure(
                    FeedbackGenerationException.TURN_COUNT_MISMATCH,
                    "AI feedback response covered %s of %d turns."
                            .formatted(turnFeedbacks == null ? "none" : turnFeedbacks.size(), trace.turnCount()),
                    content,
                    trace,
                    llmCalls
            );
        }

        if (payload.overall() == null || payload.overall().isBlank()) {
            throw failure(
                    FeedbackGenerationException.EMPTY_OVERALL,
                    "AI returned no overall feedback.",
                    content,
                    trace,
                    llmCalls
            );
        }

        return new AttemptFeedback(turnFeedbacks, payload.overall().trim(), llmCalls);
    }

    private CallTrace traceOf(Long attemptId, int turnCount, OpenAiChatOptions chatOptions, ChatResponse response) {
        Generation result = response == null ? null : response.getResult();
        ChatGenerationMetadata metadata = result == null ? null : result.getMetadata();

        return new CallTrace(
                attemptId,
                turnCount,
                chatOptions.getMaxCompletionTokens(),
                metadata == null ? null : metadata.getFinishReason(),
                completionTokensOf(response)
        );
    }

    private String contentOf(ChatResponse response) {
        Generation result = response == null ? null : response.getResult();

        if (result == null || result.getOutput() == null) {
            return null;
        }

        return result.getOutput().getText();
    }

    private Integer completionTokensOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }

        Usage usage = response.getMetadata().getUsage();

        return usage == null ? null : usage.getCompletionTokens();
    }

    private FeedbackGenerationException failure(
            String reason,
            String message,
            String content,
            CallTrace trace,
            List<LlmCallUsage> llmCalls
    ) {
        return failure(reason, message, content, trace, llmCalls, null);
    }

    /**
     * 실패를 로그로 남기고 던질 예외를 만든다. 502 응답 본문에는 원인이 실리지 않으므로 로그가 유일한 단서다.
     *
     * <p>다시 부르면 통과할 여지가 있는 실패는 {@link FeedbackResponseException}으로 구분해 던진다.
     */
    private FeedbackGenerationException failure(
            String reason,
            String message,
            String content,
            CallTrace trace,
            List<LlmCallUsage> llmCalls,
            Throwable cause
    ) {
        FeedbackGenerationException exception = RETRYABLE_REASONS.contains(reason)
                ? new FeedbackResponseException(reason, message, cause, llmCalls)
                : new FeedbackGenerationException(reason, message, cause, llmCalls);

        log.error(
                "[OPENAI FEEDBACK] generation failed | reason={} | attemptId={} | turns={} | finishReason={}"
                        + " | completionTokens={} | maxCompletionTokens={} | responseBody={}",
                reason,
                trace.attemptId(),
                trace.turnCount(),
                trace.finishReason(),
                trace.completionTokens(),
                trace.maxCompletionTokens(),
                LogFormats.abbreviate(content),
                exception
        );

        return exception;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
