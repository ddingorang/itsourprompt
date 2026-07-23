package com.promptstudio.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptstudio.ai.dto.AiCodeResult;
import com.promptstudio.ai.exception.AiProviderException;
import com.promptstudio.ai.exception.AiRequestTimeoutException;
import com.promptstudio.problem.entity.Problem;
import com.promptstudio.problem.entity.ProblemFile;
import com.openai.errors.OpenAIServiceException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AiCodeService {

    private static final long AI_REQUEST_TIMEOUT_MINUTES = 5;
    private static final int MAX_LOGGED_RESPONSE_BODY_LENGTH = 4_000;
    private static final Logger log = LoggerFactory.getLogger(AiCodeService.class);

    private final ChatClient chatClient;
    private final String nvidiaBaseUrl;
    private final String nvidiaModel;
    private final int maxTokens;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService aiExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AiCodeService(
            ChatClient.Builder chatClientBuilder,
            @Value("${spring.ai.openai.base-url}") String nvidiaBaseUrl,
            @Value("${spring.ai.openai.chat.model}") String nvidiaModel,
            @Value("${spring.ai.openai.chat.max-tokens}") int maxTokens
    ) {
        this.chatClient = chatClientBuilder.build();
        this.nvidiaBaseUrl = nvidiaBaseUrl;
        this.nvidiaModel = nvidiaModel;
        this.maxTokens = maxTokens;
    }

    public AiCodeResult generateCode(Problem problem, String userPrompt) {
        String systemPrompt = createSystemPrompt();
        String requestPrompt = createUserPrompt(problem, userPrompt);
        long startedAt = System.nanoTime();

        log.info(
                "[NVIDIA RUN] request started | model={} | endpoint={} | maxTokens={} | inputChars={} (system={}, user={}) | files={}",
                nvidiaModel,
                createChatCompletionsUrl(),
                maxTokens,
                systemPrompt.length() + requestPrompt.length(),
                systemPrompt.length(),
                requestPrompt.length(),
                problem.files().size()
        );

        Future<String> responseFuture = aiExecutor.submit(() -> chatClient.prompt()
                .system(systemPrompt)
                .user(requestPrompt)
                .call()
                .content());

        try {
            String rawResponse = responseFuture.get(AI_REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            log.info(
                    "[NVIDIA RUN] response received | duration={} ms | outputChars={}",
                    elapsedMillis(startedAt),
                    rawResponse == null ? 0 : rawResponse.length()
            );
            AiCodeResult result = parseAndValidateResponse(rawResponse);
            log.info(
                    "[NVIDIA RUN] response parsed | finalFiles={} | aiResponseChars={}",
                    result.files().size(),
                    result.aiResponse().length()
            );
            return result;
        } catch (TimeoutException exception) {
            responseFuture.cancel(true);
            log.error(
                    "[NVIDIA RUN] timed out | endpoint={} | duration={} ms | timeout={} min",
                    createChatCompletionsUrl(),
                    elapsedMillis(startedAt),
                    AI_REQUEST_TIMEOUT_MINUTES
            );
            throw new AiRequestTimeoutException();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("AI 코드 생성 요청이 중단되었습니다.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            logProviderFailure(cause);
            throw new AiProviderException("AI 코드 생성 요청에 실패했습니다.", cause);
        }
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

    private String createSystemPrompt() {
        return """
                당신은 Java 코드 생성 도우미입니다.
                사용자 요청과 문제 명세에 맞게 제공된 프로젝트 파일을 수정하세요.
                반드시 아래 JSON 객체만 반환하세요. Markdown 코드 블록이나 JSON 외의 문장은 금지합니다.

                {
                  "files": [
                    { "path": "상대 경로", "content": "파일 전체 내용" }
                  ],
                  "aiResponse": "수행한 작업을 한국어로 짧게 요약"
                }

                files에는 수정하지 않은 기존 파일도 반드시 모두 포함해야 합니다.
                삭제할 파일은 files에서 제외합니다.
                파일 경로는 상대 경로만 사용하고, 절대 경로나 .. 경로는 사용하지 마세요.
                """;
    }

    private String createUserPrompt(Problem problem, String userPrompt) {
        StringBuilder message = new StringBuilder();
        message.append("\n\n[현재 프로젝트 파일]\n");

        for (ProblemFile file : problem.files()) {
            message.append("--- ")
                    .append(file.path())
                    .append(" ---\n")
                    .append(file.content())
                    .append("\n");
        }

        message.append("\n[사용자 요청]\n")
                .append(userPrompt);

        return message.toString();
    }

    private AiCodeResult parseAndValidateResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new AiProviderException("AI가 빈 응답을 반환했습니다.");
        }

        try {
            AiCodeResult result = objectMapper.readValue(rawResponse, AiCodeResult.class);
            validateFiles(result.files());

            if (result.aiResponse() == null || result.aiResponse().isBlank()) {
                throw new AiProviderException("AI 응답에 작업 요약이 없습니다.");
            }

            return result;
        } catch (JsonProcessingException exception) {
            log.warn("NVIDIA AI가 JSON이 아닌 응답을 반환했습니다. responseBody={}",
                    abbreviate(rawResponse));
            throw new AiProviderException("AI가 올바른 JSON 형식의 응답을 반환하지 않았습니다.", exception);
        }
    }

    private void validateFiles(List<ProblemFile> files) {
        if (files == null || files.isEmpty()) {
            throw new AiProviderException("AI 응답에 최종 파일이 없습니다.");
        }

        Set<String> paths = new HashSet<>();

        for (ProblemFile file : files) {
            if (file == null || file.path() == null || file.path().isBlank()) {
                throw new AiProviderException("AI 응답에 유효하지 않은 파일 경로가 있습니다.");
            }

            if (file.path().startsWith("/") || file.path().contains("\\") || file.path().contains("..")) {
                throw new AiProviderException("AI 응답에 허용되지 않는 파일 경로가 있습니다.");
            }

            if (file.content() == null) {
                throw new AiProviderException("AI 응답에 파일 내용이 없습니다.");
            }

            if (!paths.add(file.path())) {
                throw new AiProviderException("AI 응답에 중복된 파일 경로가 있습니다.");
            }
        }
    }

    private void logProviderFailure(Throwable cause) {
        OpenAIServiceException openAiException = findOpenAiServiceException(cause);

        if (openAiException != null) {
            log.error(
                    "[NVIDIA RUN] request failed | endpoint={} | status={} | code={} | type={} | param={} | requestId={} | responseBody={}",
                    createChatCompletionsUrl(),
                    openAiException.statusCode(),
                    openAiException.code().orElse("none"),
                    openAiException.type().orElse("none"),
                    openAiException.param().orElse("none"),
                    openAiException.headers().values("x-request-id"),
                    abbreviate(String.valueOf(openAiException.body())),
                    cause
            );
            return;
        }

        RestClientResponseException responseException = findResponseException(cause);

        if (responseException != null) {
            log.error(
                    "NVIDIA API 호출 실패: status={}, responseBody={}",
                    responseException.getStatusCode().value(),
                    abbreviate(responseException.getResponseBodyAsString()),
                    cause
            );
            return;
        }

        log.error("NVIDIA API 호출 중 응답 상태를 확인할 수 없는 예외가 발생했습니다.", cause);
    }

    private OpenAIServiceException findOpenAiServiceException(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof OpenAIServiceException openAiException) {
                return openAiException;
            }

            current = current.getCause();
        }

        return null;
    }

    private RestClientResponseException findResponseException(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof RestClientResponseException responseException) {
                return responseException;
            }

            current = current.getCause();
        }

        return null;
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= MAX_LOGGED_RESPONSE_BODY_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_LOGGED_RESPONSE_BODY_LENGTH) + "... (truncated)";
    }

    @PreDestroy
    public void shutdown() {
        aiExecutor.shutdownNow();
    }
}
