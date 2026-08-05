package com.promptstudio.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptstudio.ai.LiveSessions.Expected;
import com.promptstudio.ai.LiveSessions.Session;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.port.FeedbackGenerationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * 실제 모델을 불러 피드백 응답의 성능을 재는 하네스. 유닛 테스트가 아니라 측정 도구다 —
 * 통과·실패로 판정하지 않고 숫자를 남긴다. 합격선 판정은 사람이 그 숫자를 보고 한다.
 *
 * <p>{@code llm} 태그가 붙어 있어 {@code test} 태스크에서는 제외되고 {@code llmTest}에서만 돈다.
 * 실호출은 과금되므로 호출량은 {@code -PllmReps}로만 늘린다.
 *
 * <pre>
 * set -a; . ../.env; set +a
 * sh gradlew llmTest -PllmPhase=0 -PllmReps=2
 * </pre>
 *
 * <p>페이즈는 무엇을 재는지를 가른다.
 * <ul>
 *   <li>{@code 0} — 변경 전 기준선. 계약 성공률과 판정 정확도의 대조군이다.
 *   <li>{@code A} — 근거 인용 필드를 넣은 뒤. 인용 대조 지표가 붙는다.
 *   <li>{@code B} — 턴별 채점 결과를 실은 뒤. 누출과 판정 정확도가 붙는다.
 * </ul>
 */
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class LiveFeedbackHarnessTest {

    private static final String DEFAULT_MODEL = "gpt-5.6-luna";
    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(6);
    private static final int MAX_RETRIES = 2;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AiCallExecutor executor = new AiCallExecutor();
    private final String model = envOrDefault("OPENAI_FEEDBACK_MODEL", DEFAULT_MODEL);
    private final OpenAiChatOptionsFactory optionsFactory =
            new OpenAiChatOptionsFactory(model, model, model);

    @Test
    void 실제_모델을_불러_페이즈_지표를_남긴다() throws Exception {
        String phase = System.getProperty("llmPhase", "0");
        int reps = Integer.parseInt(System.getProperty("llmReps", "1"));

        System.out.printf("[harness] phase=%s reps=%d model=%s%n", phase, reps, model);

        List<CallRecord> records = switch (phase) {
            case "0", "A" -> runBothLenses(phase, reps);
            default -> throw new IllegalArgumentException("알 수 없는 페이즈입니다: " + phase);
        };

        write(phase, records);
        summarize(phase, records);
    }

    /**
     * 두 렌즈를 세션마다 병렬로 굽는다. 세션은 순차로 돈다 — 열 호출을 한꺼번에 던지면 레이트 리밋 실패가
     * 계약 실패로 잘못 잡힌다.
     */
    private List<CallRecord> runBothLenses(String phase, int reps) {
        ChatClient.Builder chatClientBuilder = chatClientBuilder();
        List<CallRecord> records = new ArrayList<>();

        for (int rep = 1; rep <= reps; rep++) {
            for (Session session : LiveSessions.all()) {
                int currentRep = rep;
                Future<CallRecord> promptCall = executor.submit(
                        () -> call(phase, currentRep, session, Lens.PROMPT, chatClientBuilder));
                Future<CallRecord> patternCall = executor.submit(
                        () -> call(phase, currentRep, session, Lens.PATTERN, chatClientBuilder));

                records.add(await(promptCall));
                records.add(await(patternCall));
            }
        }

        return records;
    }

    private CallRecord await(Future<CallRecord> call) {
        try {
            return call.get();
        } catch (Exception exception) {
            throw new IllegalStateException("하네스 호출이 끝나지 않았습니다.", exception);
        }
    }

    /**
     * 호출 하나의 결과. 생성기가 던진 실패도 계약 실패 한 줄로 남기고 계속 돈다 — 실패율 자체가 지표다.
     */
    private CallRecord call(String phase, int rep, Session session, Lens lens, ChatClient.Builder builder) {
        OpenAiFeedbackGenerator generator = generatorFor(lens, builder);
        long startedAt = System.nanoTime();

        try {
            FeedbackDraft draft = generator.generate(session.problem(), session.attempt());

            return succeeded(phase, rep, session, lens, draft, startedAt);
        } catch (FeedbackGenerationException exception) {
            return failed(phase, rep, session, lens, exception.reason(), exception.llmCalls(), startedAt);
        } catch (RuntimeException exception) {
            return failed(phase, rep, session, lens, exception.getClass().getSimpleName(), List.of(), startedAt);
        }
    }

    private CallRecord succeeded(
            String phase,
            int rep,
            Session session,
            Lens lens,
            FeedbackDraft draft,
            long startedAt
    ) {
        List<String> judgements = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        int scored = 0;
        int hits = 0;
        int decisiveScored = 0;
        int decisiveHits = 0;

        for (int index = 0; index < draft.turnFeedbacks().size(); index++) {
            String judgement = judgementOf(draft.turnFeedbacks().get(index));
            judgements.add(judgement);

            Expected turnExpectation = index < session.turns().size() ? session.turns().get(index) : null;
            String wanted = lens == Lens.PROMPT && turnExpectation != null
                    ? expectedFor(phase, turnExpectation)
                    : null;
            expected.add(wanted);

            if (wanted == null) {
                continue;
            }

            scored++;
            boolean hit = wanted.equals(judgement);

            if (hit) {
                hits++;
            }

            if (turnExpectation.decisive()) {
                decisiveScored++;

                if (hit) {
                    decisiveHits++;
                }
            }
        }

        return new CallRecord(
                phase,
                rep,
                session.name(),
                lens.label,
                true,
                null,
                draft.turnFeedbacks().size(),
                completionTokensOf(draft.llmCalls()),
                draft.llmCalls().size(),
                elapsedMillis(startedAt),
                judgements,
                expected,
                scored,
                hits,
                decisiveScored,
                decisiveHits,
                lens == Lens.PROMPT && lastTurnSentencePresent(draft.turnFeedbacks()));
    }

    private CallRecord failed(
            String phase,
            int rep,
            Session session,
            Lens lens,
            String reason,
            List<LlmCallUsage> llmCalls,
            long startedAt
    ) {
        System.out.printf("[harness] %s %s %s 계약 실패 | reason=%s%n", phase, session.name(), lens.label, reason);

        return new CallRecord(
                phase,
                rep,
                session.name(),
                lens.label,
                false,
                reason,
                session.attempt().turns().size(),
                completionTokensOf(llmCalls),
                llmCalls.size(),
                elapsedMillis(startedAt),
                List.of(),
                List.of(),
                0,
                0,
                0,
                0,
                false);
    }

    /**
     * 무신호 페이즈(0·A)는 파일만 보고 내릴 수 있는 판정을, 신호 페이즈(B)는 채점 결과를 실었을 때의
     * 판정을 정답으로 본다. 결정적 턴은 이 둘이 갈리는 턴이다.
     */
    private String expectedFor(String phase, Expected expectation) {
        return "B".equals(phase) ? expectation.withSignal() : expectation.withoutSignal();
    }

    /**
     * 고정 문장 4종을 verbatim으로 찾는다. 단독 줄이 정본이라 그것부터 보고, 없으면 본문 어디에 있는지 본다.
     */
    private String judgementOf(String turnFeedback) {
        if (turnFeedback == null) {
            return null;
        }

        for (String line : turnFeedback.split("\n")) {
            String trimmed = line.trim();

            for (String judgement : LiveSessions.JUDGEMENTS) {
                if (trimmed.equals(judgement)) {
                    return judgement;
                }
            }
        }

        for (String judgement : LiveSessions.JUDGEMENTS) {
            if (turnFeedback.contains(judgement)) {
                return judgement;
            }
        }

        return null;
    }

    private boolean lastTurnSentencePresent(List<String> turnFeedbacks) {
        if (turnFeedbacks.isEmpty()) {
            return false;
        }

        return turnFeedbacks.getLast().contains(LiveSessions.LAST_TURN_OWNERSHIP);
    }

    private Integer completionTokensOf(List<LlmCallUsage> llmCalls) {
        if (llmCalls.isEmpty()) {
            return null;
        }

        long total = 0;

        for (LlmCallUsage usage : llmCalls) {
            if (usage.outputTokens() != null) {
                total += usage.outputTokens();
            }
        }

        return (int) total;
    }

    private OpenAiFeedbackGenerator generatorFor(Lens lens, ChatClient.Builder builder) {
        if (lens == Lens.PROMPT) {
            return new OpenAiFeedbackGenerator(
                    builder,
                    executor,
                    "OPENAI FEEDBACK",
                    FeedbackPrompts.systemPrompt(),
                    FeedbackPrompts::userPrompt,
                    optionsFactory::forFeedback);
        }

        return new OpenAiFeedbackGenerator(
                builder,
                executor,
                "OPENAI PATTERN",
                PatternPrompts.systemPrompt(),
                PatternPrompts::userPrompt,
                optionsFactory::forPatternFeedback);
    }

    /**
     * 운영과 같은 경로로 조립한다. spring-ai 2.0.0의 OpenAI 클라이언트는 {@code OpenAiSetup}이 만든다 —
     * 이 배포에는 {@code openai-java-client-okhttp}가 없고 spring-ai가 자기 okhttp 클라이언트를 쓴다.
     */
    private ChatClient.Builder chatClientBuilder() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiClient(OpenAiSetup.setupSyncClient(
                        System.getenv("OPENAI_BASE_URL"),
                        System.getenv("OPENAI_API_KEY"),
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        model,
                        CALL_TIMEOUT,
                        MAX_RETRIES,
                        null,
                        null,
                        ObservationRegistry.NOOP,
                        new SimpleMeterRegistry(),
                        List.of()))
                .options(OpenAiChatOptions.builder().model(model).build())
                .build();

        return ChatClient.builder(chatModel);
    }

    private void write(String phase, List<CallRecord> records) throws IOException {
        Path directory = Path.of("build", "llm-harness");
        Files.createDirectories(directory);
        Path file = directory.resolve(phase + ".jsonl");
        StringBuilder lines = new StringBuilder();

        for (CallRecord record : records) {
            lines.append(objectMapper.writeValueAsString(record)).append("\n");
        }

        Files.writeString(file, lines.toString());
        System.out.printf("[harness] wrote %d records to %s%n", records.size(), file.toAbsolutePath());
    }

    private void summarize(String phase, List<CallRecord> records) {
        int calls = records.size();
        int ok = 0;
        int scored = 0;
        int hits = 0;
        int decisiveScored = 0;
        int decisiveHits = 0;
        long tokens = 0;

        for (CallRecord record : records) {
            if (record.ok()) {
                ok++;
            }

            scored += record.judgementScored();
            hits += record.judgementHits();
            decisiveScored += record.decisiveScored();
            decisiveHits += record.decisiveHits();
            tokens += record.completionTokens() == null ? 0 : record.completionTokens();
        }

        System.out.println("[harness] ===== phase " + phase + " =====");
        System.out.printf("[harness] 계약 성공 %d/%d (%.1f%%)%n", ok, calls, percent(ok, calls));
        System.out.printf("[harness] 판정1 정확도 %d/%d (%.1f%%)%n", hits, scored, percent(hits, scored));
        System.out.printf("[harness] 결정적 턴 판정1 %d/%d%n", decisiveHits, decisiveScored);
        System.out.printf("[harness] 완성 토큰 합계 %d%n", tokens);
    }

    private double percent(int part, int total) {
        return total == 0 ? 0 : part * 100.0 / total;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);

        return value == null || value.isBlank() ? fallback : value;
    }

    private enum Lens {

        PROMPT("prompt"),
        PATTERN("pattern");

        private final String label;

        Lens(String label) {
            this.label = label;
        }
    }

    /**
     * 호출 한 건의 측정 결과. jsonl 한 줄이 이 레코드 하나다.
     *
     * @param judgementScored 정답을 아는 턴 수(프롬프트 렌즈만)
     * @param decisiveScored  신호가 판정을 뒤집는 턴 수
     */
    record CallRecord(
            String phase,
            int rep,
            String session,
            String lens,
            boolean ok,
            String reason,
            int turns,
            Integer completionTokens,
            int llmCalls,
            long elapsedMs,
            List<String> judgements,
            List<String> expected,
            int judgementScored,
            int judgementHits,
            int decisiveScored,
            int decisiveHits,
            boolean lastTurnSentence
    ) {
    }
}
