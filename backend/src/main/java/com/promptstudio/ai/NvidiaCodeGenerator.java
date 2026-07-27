package com.promptstudio.ai;

import com.openai.errors.OpenAIServiceException;
import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.port.CodeGenerator;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.port.CodeGenerationException;
import com.promptstudio.attempt.port.CodeGenerationTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class NvidiaCodeGenerator implements CodeGenerator {

    private static final long AI_REQUEST_TIMEOUT_MINUTES = 5;
    private static final Logger log = LoggerFactory.getLogger(NvidiaCodeGenerator.class);

    private final ChatClient chatClient;
    private final AiCallExecutor aiCallExecutor;
    private final String nvidiaBaseUrl;
    private final String nvidiaModel;
    private final int maxTokens;

    public NvidiaCodeGenerator(
            ChatClient.Builder chatClientBuilder,
            AiCallExecutor aiCallExecutor,
            @Value("${spring.ai.openai.base-url}") String nvidiaBaseUrl,
            @Value("${spring.ai.openai.chat.model}") String nvidiaModel,
            @Value("${spring.ai.openai.chat.max-tokens}") int maxTokens
    ) {
        this.chatClient = chatClientBuilder.build();
        this.aiCallExecutor = aiCallExecutor;
        this.nvidiaBaseUrl = nvidiaBaseUrl;
        this.nvidiaModel = nvidiaModel;
        this.maxTokens = maxTokens;
    }

    @Override
    public GeneratedCode generate(Attempt attempt, String userPrompt) {
        List<Message> messages = CodeGenerationPrompts.messages(attempt, userPrompt);
        long startedAt = System.nanoTime();

        log.info(
                "[NVIDIA RUN] request started | model={} | endpoint={} | maxTokens={} | inputChars={} | attemptId={} | turns={} | files={}",
                nvidiaModel,
                createChatCompletionsUrl(),
                maxTokens,
                totalChars(messages),
                attempt.id(),
                attempt.turns().size(),
                attempt.currentFiles().size()
        );

        try {
            String rawResponse = aiCallExecutor.call(() -> chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content(), AI_REQUEST_TIMEOUT_MINUTES);
            log.info(
                    "[NVIDIA RUN] response received | duration={} ms | outputChars={}",
                    elapsedMillis(startedAt),
                    rawResponse == null ? 0 : rawResponse.length()
            );
            GeneratedCode result = GeneratedCodeParser.parse(rawResponse);
            log.info(
                    "[NVIDIA RUN] response parsed | finalFiles={} | aiResponseChars={}",
                    result.files().size(),
                    result.summary().length()
            );
            return result;
        } catch (TimeoutException exception) {
            log.error(
                    "[NVIDIA RUN] timed out | endpoint={} | duration={} ms | timeout={} min",
                    createChatCompletionsUrl(),
                    elapsedMillis(startedAt),
                    AI_REQUEST_TIMEOUT_MINUTES
            );
            throw new CodeGenerationTimeoutException();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CodeGenerationException("AI 코드 생성 요청이 중단되었습니다.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            logProviderFailure(cause);
            throw new CodeGenerationException("AI 코드 생성 요청에 실패했습니다.", cause);
        }
    }

    private int totalChars(List<Message> messages) {
        int total = 0;

        for (Message message : messages) {
            total += message.getText().length();
        }

        return total;
    }

    private String createChatCompletionsUrl() {
        String normalizedBaseUrl = nvidiaBaseUrl.endsWith("/")
                ? nvidiaBaseUrl.substring(0, nvidiaBaseUrl.length() - 1)
                : nvidiaBaseUrl;

        if (normalizedBaseUrl.endsWith("/v1")) {
            return normalizedBaseUrl + "/chat/completions";
        }

        return normalizedBaseUrl + "/v1/chat/completions";
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private void logProviderFailure(Throwable cause) {
        OpenAIServiceException openAiException = findCause(cause, OpenAIServiceException.class);

        if (openAiException != null) {
            log.error(
                    "[NVIDIA RUN] request failed | endpoint={} | status={} | code={} | type={} | param={} | requestId={} | responseBody={}",
                    createChatCompletionsUrl(),
                    openAiException.statusCode(),
                    openAiException.code().orElse("none"),
                    openAiException.type().orElse("none"),
                    openAiException.param().orElse("none"),
                    openAiException.headers().values("x-request-id"),
                    GeneratedCodeParser.abbreviate(String.valueOf(openAiException.body())),
                    cause
            );
            return;
        }

        RestClientResponseException responseException = findCause(cause, RestClientResponseException.class);

        if (responseException != null) {
            log.error(
                    "NVIDIA API 호출 실패: status={}, responseBody={}",
                    responseException.getStatusCode().value(),
                    GeneratedCodeParser.abbreviate(responseException.getResponseBodyAsString()),
                    cause
            );
            return;
        }

        log.error("NVIDIA API 호출 중 응답 상태를 확인할 수 없는 예외가 발생했습니다.", cause);
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;

        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }

            current = current.getCause();
        }

        return null;
    }
}
