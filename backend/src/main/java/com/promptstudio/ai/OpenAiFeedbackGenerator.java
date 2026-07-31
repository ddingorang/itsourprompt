package com.promptstudio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptView;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OpenAiFeedbackGenerator implements FeedbackGenerator {

    private static final long FEEDBACK_TIMEOUT_MINUTES = 5;
    private static final String TRUNCATED_FINISH_REASON = "length";
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
        long startedAt = System.nanoTime();

        log.info(
                "[OPENAI FEEDBACK] request started | model={} | problemId={} | attemptId={} | inputChars={} | turns={}",
                chatOptions.getModel(),
                problem.id(),
                attempt.id(),
                systemPrompt.length() + userPrompt.length(),
                turnCount
        );

        try {
            ChatResponse response = aiCallExecutor.call(() -> chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(chatOptions.mutate())
                    .call()
                    .chatResponse(), FEEDBACK_TIMEOUT_MINUTES);

            CallTrace trace = traceOf(attempt.id(), turnCount, chatOptions, response);
            String content = contentOf(response);
            AttemptFeedback feedback = parse(content, trace);

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
            throw new FeedbackTimeoutException();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FeedbackGenerationException(
                    FeedbackGenerationException.INTERRUPTED,
                    "AI feedback request was interrupted.",
                    exception
            );
        } catch (ExecutionException exception) {
            log.error(
                    "[OPENAI FEEDBACK] generation failed | reason={} | attemptId={} | turns={} | duration={} ms",
                    FeedbackGenerationException.PROVIDER_ERROR,
                    attempt.id(),
                    turnCount,
                    elapsedMillis(startedAt),
                    exception.getCause()
            );
            throw new FeedbackGenerationException(
                    FeedbackGenerationException.PROVIDER_ERROR,
                    "AI feedback request failed.",
                    exception.getCause()
            );
        }
    }

    /**
     * 턴과 피드백의 1:1 대응이 응답의 유일한 계약이므로 개수가 어긋나면 저장하지 않는다.
     *
     * <p>잘린 응답은 본문만 보면 정상 JSON일 수 있어 먼저 걸러낸다.
     */
    private AttemptFeedback parse(String content, CallTrace trace) {
        if (trace.truncated()) {
            throw failure(
                    FeedbackGenerationException.TRUNCATED,
                    "AI feedback response was cut off at the completion token limit.",
                    content,
                    trace
            );
        }

        if (content == null || content.isBlank()) {
            throw failure(
                    FeedbackGenerationException.EMPTY_CONTENT,
                    "AI returned an empty feedback response.",
                    content,
                    trace
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
                    trace
            );
        }

        if (payload.overall() == null || payload.overall().isBlank()) {
            throw failure(
                    FeedbackGenerationException.EMPTY_OVERALL,
                    "AI returned no overall feedback.",
                    content,
                    trace
            );
        }

        return new AttemptFeedback(turnFeedbacks, payload.overall().trim());
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

    private FeedbackGenerationException failure(String reason, String message, String content, CallTrace trace) {
        return failure(reason, message, content, trace, null);
    }

    /**
     * 실패를 로그로 남기고 던질 예외를 만든다. 502 응답 본문에는 원인이 실리지 않으므로 로그가 유일한 단서다.
     */
    private FeedbackGenerationException failure(
            String reason,
            String message,
            String content,
            CallTrace trace,
            Throwable cause
    ) {
        FeedbackGenerationException exception = new FeedbackGenerationException(reason, message, cause);

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
