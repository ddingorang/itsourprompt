package com.promptstudio.ai;

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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OpenAiFeedbackGenerator implements FeedbackGenerator {

    private static final long FEEDBACK_TIMEOUT_MINUTES = 5;
    private static final Logger log = LoggerFactory.getLogger(OpenAiFeedbackGenerator.class);

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

    @Override
    public String generate(ProblemView problem, AttemptView attempt) {
        String systemPrompt = FeedbackPrompts.systemPrompt();
        String userPrompt = FeedbackPrompts.userPrompt(problem, attempt);
        OpenAiChatOptions chatOptions = chatOptionsFactory.forFeedback();
        long startedAt = System.nanoTime();

        log.info(
                "[OPENAI FEEDBACK] request started | model={} | problemId={} | attemptId={} | inputChars={} | turns={}",
                chatOptions.getModel(),
                problem.id(),
                attempt.id(),
                systemPrompt.length() + userPrompt.length(),
                attempt.turns().size()
        );

        try {
            String feedback = aiCallExecutor.call(() -> chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(chatOptions.mutate())
                    .call()
                    .content(), FEEDBACK_TIMEOUT_MINUTES);

            if (feedback == null || feedback.isBlank()) {
                throw new FeedbackGenerationException("AI returned an empty feedback response.");
            }

            log.info(
                    "[OPENAI FEEDBACK] response received | duration={} ms | feedbackChars={}",
                    elapsedMillis(startedAt),
                    feedback.length()
            );
            return feedback.trim();
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

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
