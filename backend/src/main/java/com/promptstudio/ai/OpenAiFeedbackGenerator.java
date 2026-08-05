package com.promptstudio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.LlmCallUsage;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

/**
 * 피드백 렌즈 하나를 굽는 어댑터. 렌즈마다 다른 것은 시스템 프롬프트·유저 프롬프트 조립·호출 옵션·로그
 * 태그 넷뿐이라, 클래스를 복제하지 않고 그 넷만 생성자로 받는다 — 복제하면 응답 계약을 고칠 때마다
 * 두 곳을 고쳐야 한다.
 *
 * <p>포트({@link com.promptstudio.attempt.port.FeedbackGenerator})를 구현하지 않는다. 구현이 둘이 되면
 * 운영 컨텍스트가 부팅에 실패하므로 포트는 {@link FeedbackGenerators} 하나만 구현한다.
 */
class OpenAiFeedbackGenerator {

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
            FeedbackGenerationException.EMPTY_OVERALL,
            FeedbackGenerationException.QUOTE_NOT_FOUND
    );

    private static final Logger log = LoggerFactory.getLogger(OpenAiFeedbackGenerator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;
    private final AiCallExecutor aiCallExecutor;
    private final String logTag;
    private final String systemPrompt;
    private final BiFunction<ProblemView, AttemptView, String> userPrompt;
    private final IntFunction<OpenAiChatOptions> chatOptions;

    OpenAiFeedbackGenerator(
            ChatClient.Builder chatClientBuilder,
            AiCallExecutor aiCallExecutor,
            String logTag,
            String systemPrompt,
            BiFunction<ProblemView, AttemptView, String> userPrompt,
            IntFunction<OpenAiChatOptions> chatOptions
    ) {
        this.chatClient = chatClientBuilder.build();
        this.aiCallExecutor = aiCallExecutor;
        this.logTag = logTag;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.chatOptions = chatOptions;
    }

    private record FeedbackPayload(List<TurnEntry> turnFeedbacks, String overall) {
    }

    /**
     * 턴 하나의 응답. 인용은 대조에만 쓰고 저장 봉투로 넘기지 않는다 — {@link FeedbackDraft}와
     * {@link com.promptstudio.attempt.domain.AttemptFeedback}, DB, FE 응답은 그대로다.
     *
     * @param quotes 이 턴의 판정을 뒷받침한다고 모델이 주장하는 입력 문장들
     */
    record TurnEntry(List<String> quotes, String feedback) {
    }

    /**
     * 파싱이 끝난 응답. 인용 대조는 조립한 유저 프롬프트를 들고 있는 {@link #callAndParse}에서 하므로
     * 원본 턴 항목을 draft와 함께 돌려준다.
     */
    private record ParsedFeedback(List<TurnEntry> turns, FeedbackDraft draft) {
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

    FeedbackDraft generate(ProblemView problem, AttemptView attempt) {
        String userMessage = userPrompt.apply(problem, attempt);
        int turnCount = attempt.turns().size();
        OpenAiChatOptions options = chatOptions.apply(turnCount);

        log.info(
                "[{}] request started | model={} | problemId={} | attemptId={} | inputChars={} | turns={}",
                logTag,
                options.getModel(),
                problem.id(),
                attempt.id(),
                systemPrompt.length() + userMessage.length(),
                turnCount
        );

        LlmUsageTracker tracker = new LlmUsageTracker();

        try {
            return callAndParse(userMessage, options, attempt.id(), turnCount, tracker);
        } catch (FeedbackResponseException exception) {
            log.warn(
                    "[{}] response contract broken, retrying once | attemptId={} | turns={} | reason={}",
                    logTag,
                    attempt.id(),
                    turnCount,
                    exception.reason()
            );

            return callAndParse(userMessage, options, attempt.id(), turnCount, tracker);
        }
    }

    /**
     * 응답 형태가 어긋나는 실패는 같은 요청을 다시 보내면 통과하는 경우가 많아 {@link #generate}가 이 호출을
     * 한 번 더 시도한다. 재시도 자체를 여기 두지 않는 이유는 상한을 한 번으로 못 박기 위해서다.
     *
     * <p>사용량 tracker는 호출 사이에 이어져야 한다 — 재시도로 끝난 제출도 두 호출이 쓴 토큰을 모두 싣는다.
     */
    private FeedbackDraft callAndParse(
            String userMessage,
            OpenAiChatOptions options,
            Long attemptId,
            int turnCount,
            LlmUsageTracker tracker
    ) {
        long startedAt = System.nanoTime();

        try {
            ChatResponse response = aiCallExecutor.call(() -> chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .options(options.mutate())
                    .call()
                    .chatResponse(), FEEDBACK_TIMEOUT_MINUTES);

            tracker.record(options.getModel(), response, elapsedMillis(startedAt));

            CallTrace trace = traceOf(attemptId, turnCount, options, response);
            String content = contentOf(response);
            ParsedFeedback parsed = parse(content, trace, tracker.snapshot());
            QuoteVerifier.Result quotes = QuoteVerifier.verify(userMessage, parsed.turns());
            logQuoteCheck(quotes, attemptId, turnCount);

            if (!quotes.grounded()) {
                throw failure(
                        FeedbackGenerationException.QUOTE_NOT_FOUND,
                        "AI feedback quoted %d line(s) that are not in the input."
                                .formatted(quotes.normalizedMisses().size()),
                        content,
                        trace,
                        tracker.snapshot()
                );
            }

            log.info(
                    "[{}] response received | duration={} ms | finishReason={} | completionTokens={}"
                            + " | feedbackChars={}",
                    logTag,
                    elapsedMillis(startedAt),
                    trace.finishReason(),
                    trace.completionTokens(),
                    content.length()
            );
            return parsed.draft();
        } catch (TimeoutException exception) {
            log.error(
                    "[{}] timed out | duration={} ms | timeout={} min",
                    logTag,
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
                    "[{}] generation failed | reason={} | attemptId={} | turns={} | duration={} ms",
                    logTag,
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
    private ParsedFeedback parse(String content, CallTrace trace, List<LlmCallUsage> llmCalls) {
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

        List<TurnEntry> turnEntries = payload.turnFeedbacks();

        if (turnEntries == null || turnEntries.size() != trace.turnCount()) {
            throw failure(
                    FeedbackGenerationException.TURN_COUNT_MISMATCH,
                    "AI feedback response covered %s of %d turns."
                            .formatted(turnEntries == null ? "none" : turnEntries.size(), trace.turnCount()),
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

        return new ParsedFeedback(
                turnEntries,
                new FeedbackDraft(feedbacksOf(turnEntries), payload.overall().trim(), llmCalls));
    }

    /**
     * 저장으로 넘기는 것은 {@code feedback} 문자열뿐이다. 인용은 대조를 마치면 버린다 — 화면에 나갈 것이
     * 아니라 응답이 입력에 붙어 있는지 재는 계량기다.
     */
    private List<String> feedbacksOf(List<TurnEntry> turnEntries) {
        List<String> feedbacks = new ArrayList<>();

        for (TurnEntry entry : turnEntries) {
            feedbacks.add(entry == null ? null : entry.feedback());
        }

        return feedbacks;
    }

    /**
     * 인용 대조 결과를 로그로 남긴다. 승격 뒤에도 남기는 이유는 운영에서 수치를 계속 쌓기 위해서다 —
     * raw·턴스코프 불일치는 계약이 아니라 진단이라 로그에만 있다.
     *
     * <p>인용 문장 자체는 남기지 않는다. 그것은 모델이 지어낸 자유 텍스트이고, 자유 텍스트는 로그가 아니라
     * DB로 간다(observability-plan §7). 로그에는 어느 턴에서 몇 자짜리가 어긋났는지만 남겨 수치를 쌓는다.
     */
    private void logQuoteCheck(QuoteVerifier.Result result, Long attemptId, int turnCount) {
        log.info(
                "[{}] quote check | attemptId={} | turns={} | quotes={} | rawMiss={} | normMiss={}"
                        + " | turnScopedMiss={}",
                logTag,
                attemptId,
                turnCount,
                result.total(),
                result.rawMisses().size(),
                result.normalizedMisses().size(),
                result.turnScopedMisses().size()
        );

        for (QuoteVerifier.Miss miss : result.normalizedMisses()) {
            log.warn(
                    "[{}] quote not found in input | attemptId={} | turn={} | quoteLength={}",
                    logTag,
                    attemptId,
                    miss.turn(),
                    miss.quote() == null ? 0 : miss.quote().length()
            );
        }
    }

    private CallTrace traceOf(Long attemptId, int turnCount, OpenAiChatOptions options, ChatResponse response) {
        Generation result = response == null ? null : response.getResult();
        ChatGenerationMetadata metadata = result == null ? null : result.getMetadata();

        return new CallTrace(
                attemptId,
                turnCount,
                options.getMaxCompletionTokens(),
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
                "[{}] generation failed | reason={} | attemptId={} | turns={} | finishReason={}"
                        + " | completionTokens={} | maxCompletionTokens={} | responseBody={}",
                logTag,
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
