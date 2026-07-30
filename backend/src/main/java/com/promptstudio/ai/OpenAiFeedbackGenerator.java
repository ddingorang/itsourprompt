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
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OpenAiFeedbackGenerator implements FeedbackGenerator {

    private static final long FEEDBACK_TIMEOUT_MINUTES = 5;
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
            String response = aiCallExecutor.call(() -> chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(chatOptions.mutate())
                    .call()
                    .content(), FEEDBACK_TIMEOUT_MINUTES);

            AttemptFeedback feedback = parse(response, turnCount);

            log.info(
                    "[OPENAI FEEDBACK] response received | duration={} ms | feedbackChars={}",
                    elapsedMillis(startedAt),
                    response.length()
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
            throw new FeedbackGenerationException("AI feedback request was interrupted.", exception);
        } catch (ExecutionException exception) {
            log.error("[OPENAI FEEDBACK] request failed", exception.getCause());
            throw new FeedbackGenerationException("AI feedback request failed.", exception.getCause());
        }
    }

    /**
     * 턴과 피드백의 1:1 대응이 응답의 유일한 계약이므로 개수가 어긋나면 저장하지 않는다.
     */
    private AttemptFeedback parse(String response, int turnCount) {
        if (response == null || response.isBlank()) {
            throw new FeedbackGenerationException("AI returned an empty feedback response.");
        }

        FeedbackPayload payload;

        try {
            payload = objectMapper.readValue(response, FeedbackPayload.class);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "[OPENAI FEEDBACK] response was not valid JSON | responseBody={}",
                    LogFormats.abbreviate(response)
            );
            throw new FeedbackGenerationException("AI feedback response was not valid JSON.", exception);
        }

        if (payload.turnFeedbacks() == null || payload.turnFeedbacks().size() != turnCount) {
            throw new FeedbackGenerationException("AI feedback response did not cover every turn.");
        }

        if (payload.overall() == null || payload.overall().isBlank()) {
            throw new FeedbackGenerationException("AI returned an empty feedback response.");
        }

        return new AttemptFeedback(payload.turnFeedbacks(), payload.overall().trim());
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
