package com.promptstudio.ai;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.Submission;
import com.promptstudio.problem.port.FeedbackGenerationException;
import com.promptstudio.problem.port.FeedbackGenerator;
import com.promptstudio.problem.port.FeedbackTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class NvidiaFeedbackGenerator implements FeedbackGenerator {

    private static final long FEEDBACK_TIMEOUT_MINUTES = 5;
    private static final Logger log = LoggerFactory.getLogger(NvidiaFeedbackGenerator.class);

    private final ChatClient chatClient;
    private final AiCallExecutor aiCallExecutor;

    public NvidiaFeedbackGenerator(ChatClient.Builder chatClientBuilder, AiCallExecutor aiCallExecutor) {
        this.chatClient = chatClientBuilder.build();
        this.aiCallExecutor = aiCallExecutor;
    }

    @Override
    public String generate(Problem problem, Submission submission) {
        String systemPrompt = FeedbackPrompts.systemPrompt();
        String userPrompt = FeedbackPrompts.userPrompt(problem, submission);
        long startedAt = System.nanoTime();

        log.info(
                "[NVIDIA FEEDBACK] request started | problemId={} | inputChars={} | changedFiles={}",
                problem.id(),
                systemPrompt.length() + userPrompt.length(),
                submission.changes().size()
        );

        try {
            String feedback = aiCallExecutor.call(() -> chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content(), FEEDBACK_TIMEOUT_MINUTES);

            if (feedback == null || feedback.isBlank()) {
                throw new FeedbackGenerationException("AI returned an empty feedback response.");
            }

            log.info(
                    "[NVIDIA FEEDBACK] response received | duration={} ms | feedbackChars={}",
                    elapsedMillis(startedAt),
                    feedback.length()
            );
            return feedback.trim();
        } catch (TimeoutException exception) {
            log.error(
                    "[NVIDIA FEEDBACK] timed out | duration={} ms | timeout={} min",
                    elapsedMillis(startedAt),
                    FEEDBACK_TIMEOUT_MINUTES
            );
            throw new FeedbackTimeoutException();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FeedbackGenerationException("AI feedback request was interrupted.", exception);
        } catch (ExecutionException exception) {
            log.error("[NVIDIA FEEDBACK] request failed", exception.getCause());
            throw new FeedbackGenerationException("AI feedback request failed.", exception.getCause());
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
