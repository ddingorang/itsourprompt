package com.promptstudio.ai;

import com.openai.errors.OpenAIServiceException;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.port.CodeGenerationException;
import com.promptstudio.attempt.port.CodeGenerationTimeoutException;
import com.promptstudio.attempt.port.CodeGenerator;
import com.promptstudio.problem.domain.ProblemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 툴콜링 수동 루프로 코드를 생성한다.
 *
 * <p>라운드 = 툴을 실은 모델 호출 1회. {@value #MAX_TOOL_ROUNDS}라운드까지 돌고, 마지막 라운드의
 * 툴콜도 실행한 뒤(OpenAI는 tool_calls 뒤에 tool 결과를 요구한다) 툴 없는 호출로 요약만 한 번 더 받는다.
 * 총 호출은 {@value #MAX_TOOL_ROUNDS} + 1회를 넘지 않으며, 전체가 {@value #AI_REQUEST_TIMEOUT_MINUTES}분
 * 예산 안에서 끝나야 한다.
 */
@Component
public class OpenAiCodeGenerator implements CodeGenerator {

    private static final int MAX_TOOL_ROUNDS = 10;
    private static final long AI_REQUEST_TIMEOUT_MINUTES = 5;
    private static final String FALLBACK_SUMMARY = "작업을 완료했지만 AI가 요약을 제공하지 않았습니다.";
    private static final String FORCED_FINALIZE_PROMPT =
            "툴 사용 한도에 도달했습니다. 지금까지 수행한 작업을 한국어로 요약해 주세요.";
    private static final Logger log = LoggerFactory.getLogger(OpenAiCodeGenerator.class);

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final AiCallExecutor aiCallExecutor;
    private final OpenAiChatOptionsFactory chatOptionsFactory;

    public OpenAiCodeGenerator(
            ChatModel chatModel,
            ToolCallingManager toolCallingManager,
            AiCallExecutor aiCallExecutor,
            OpenAiChatOptionsFactory chatOptionsFactory
    ) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.aiCallExecutor = aiCallExecutor;
        this.chatOptionsFactory = chatOptionsFactory;
    }

    /**
     * 사용량 누적은 루프 밖에서 만든다 — 타임아웃으로 루프를 버려도 그때까지 쓴 토큰을 예외에 실어 보낸다.
     */
    @Override
    public GeneratedCode generate(ProblemView problem, AttemptView attempt, String userPrompt) {
        long startedAt = System.nanoTime();
        LlmUsageTracker tracker = new LlmUsageTracker();

        try {
            return aiCallExecutor.call(
                    () -> runLoop(problem, attempt, userPrompt, tracker), AI_REQUEST_TIMEOUT_MINUTES);
        } catch (TimeoutException exception) {
            log.error(
                    "[OPENAI RUN] timed out | attemptId={} | duration={} ms | timeout={} min",
                    attempt.id(),
                    elapsedMillis(startedAt),
                    AI_REQUEST_TIMEOUT_MINUTES
            );
            throw new CodeGenerationTimeoutException(tracker.snapshot());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CodeGenerationException(
                    "AI 코드 생성 요청이 중단되었습니다.", exception, "interrupted", tracker.snapshot());
        } catch (ExecutionException exception) {
            logProviderFailure(exception.getCause());
            throw new CodeGenerationException(
                    "AI 코드 생성 요청에 실패했습니다.", exception.getCause(), "provider-error", tracker.snapshot());
        }
    }

    private GeneratedCode runLoop(
            ProblemView problem, AttemptView attempt, String userPrompt, LlmUsageTracker tracker) {
        CodeGenerationTools tools = new CodeGenerationTools(attempt.files(), problem.language());
        String promptCacheKey = "attempt-" + attempt.id();
        OpenAiChatOptions toolOptions = chatOptionsFactory.forCodeGeneration(promptCacheKey, tools.callbacks());
        Prompt prompt = new Prompt(CodeGenerationPrompts.messages(problem, attempt, userPrompt), toolOptions);

        log.info(
                "[OPENAI RUN] request started | model={} | attemptId={} | turns={} | files={}",
                toolOptions.getModel(),
                attempt.id(),
                attempt.turns().size(),
                attempt.files().size()
        );

        for (int round = 1; round <= MAX_TOOL_ROUNDS; round++) {
            ChatResponse response = callRound(prompt, round, toolOptions.getModel(), tracker);

            if (!response.hasToolCalls()) {
                return new GeneratedCode(
                        tools.currentFiles(), summaryOf(response), tools.trace(), tracker.snapshot());
            }

            ToolExecutionResult executed = toolCallingManager.executeToolCalls(prompt, response);
            prompt = new Prompt(executed.conversationHistory(), toolOptions);
        }

        log.warn(
                "[OPENAI RUN] tool round cap reached | attemptId={} | cap={} | toolCalls={} | editedFiles={}",
                attempt.id(),
                MAX_TOOL_ROUNDS,
                tools.trace().size(),
                tools.editedFileCount()
        );

        return finalizeWithoutTools(prompt.getInstructions(), tools, promptCacheKey, tracker);
    }

    private ChatResponse callRound(Prompt prompt, int round, String model, LlmUsageTracker tracker) {
        long startedAt = System.nanoTime();
        ChatResponse response = chatModel.call(prompt);
        long durationMillis = elapsedMillis(startedAt);

        tracker.record(model, response, durationMillis);

        log.info(
                "[OPENAI RUN] round finished | round={} | duration={} ms | toolCalls={}",
                round,
                durationMillis,
                toolCallNames(response)
        );

        return response;
    }

    /**
     * 상한 경로의 요약 호출이 실패해도 이미 반영된 편집을 버리지 않는다 — 대체 요약으로 턴을 저장한다.
     */
    private GeneratedCode finalizeWithoutTools(
            List<Message> history,
            CodeGenerationTools tools,
            String promptCacheKey,
            LlmUsageTracker tracker
    ) {
        List<Message> messages = new ArrayList<>(history);
        messages.add(new UserMessage(FORCED_FINALIZE_PROMPT));
        OpenAiChatOptions finalizeOptions = chatOptionsFactory.forCodeGenerationFinalize(promptCacheKey);

        try {
            long startedAt = System.nanoTime();
            ChatResponse response = chatModel.call(new Prompt(messages, finalizeOptions));

            tracker.record(finalizeOptions.getModel(), response, elapsedMillis(startedAt));

            return new GeneratedCode(
                    tools.currentFiles(), summaryOf(response), tools.trace(), tracker.snapshot());
        } catch (RuntimeException exception) {
            log.warn("[OPENAI RUN] finalize call failed | 편집을 대체 요약으로 보존합니다.", exception);

            return new GeneratedCode(tools.currentFiles(), FALLBACK_SUMMARY, tools.trace(), tracker.snapshot());
        }
    }

    private String summaryOf(ChatResponse response) {
        AssistantMessage output = outputOf(response);
        String text = output == null ? null : output.getText();

        if (text == null || text.isBlank()) {
            return FALLBACK_SUMMARY;
        }

        return text.trim();
    }

    private List<String> toolCallNames(ChatResponse response) {
        AssistantMessage output = outputOf(response);

        if (output == null) {
            return List.of();
        }

        List<String> names = new ArrayList<>();

        for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
            names.add(toolCall.name());
        }

        return names;
    }

    private AssistantMessage outputOf(ChatResponse response) {
        return response.getResult() == null ? null : response.getResult().getOutput();
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private void logProviderFailure(Throwable cause) {
        OpenAIServiceException openAiException = findCause(cause, OpenAIServiceException.class);

        if (openAiException != null) {
            log.error(
                    "[OPENAI RUN] request failed | status={} | code={} | type={} | param={} | requestId={} | responseBody={}",
                    openAiException.statusCode(),
                    openAiException.code().orElse("none"),
                    openAiException.type().orElse("none"),
                    openAiException.param().orElse("none"),
                    openAiException.headers().values("x-request-id"),
                    LogFormats.abbreviate(String.valueOf(openAiException.body())),
                    cause
            );
            return;
        }

        RestClientResponseException responseException = findCause(cause, RestClientResponseException.class);

        if (responseException != null) {
            log.error(
                    "OpenAI API 호출 실패: status={}, responseBody={}",
                    responseException.getStatusCode().value(),
                    LogFormats.abbreviate(responseException.getResponseBodyAsString()),
                    cause
            );
            return;
        }

        log.error("OpenAI API 호출 중 응답 상태를 확인할 수 없는 예외가 발생했습니다.", cause);
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
