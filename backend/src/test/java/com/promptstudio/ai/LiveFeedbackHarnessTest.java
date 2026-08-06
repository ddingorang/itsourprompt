package com.promptstudio.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptstudio.ai.LiveSessions.Expected;
import com.promptstudio.ai.LiveSessions.Session;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.domain.TurnTestResults;
import com.promptstudio.attempt.port.FeedbackGenerationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * 채점 문장 누출을 잡는 그물. 통과 개수·비율·정답 여부가 사용자 문장으로 나가면 안 된다 —
     * 사용자는 실행 화면에서 이미 그 숫자를 본다.
     */
    private static final Pattern SCORE_LEAK =
            Pattern.compile("\\d+\\s*개[^\\n]{0,8}통과|\\d+\\s*/\\s*\\d+|정답");

    /** pattern 렌즈의 절 제목 뒤 첫 줄. 그 줄이 용어로 시작한다. */
    private static final Pattern PATTERN_NAME = Pattern.compile("###\\s*이 턴의 패턴\\s*\\n\\s*([^\\n]+)");
    private static final Pattern PATTERN_TECHNIQUE = Pattern.compile("###\\s*쓸 기법\\s*\\n\\s*([^\\n]+)");

    /** 사전 용어. 긴 것부터 봐야 `human review`가 `human-in-the-loop`을 가로채지 않는다. */
    private static final List<String> TERMS = List.of(
            "human-in-the-loop", "automated review", "automated check", "design concept",
            "human review", "vibe coding", "prototyping", "grilling", "AFK", "DX", "AX");

    /**
     * 사용자를 주어로 세운 문장.
     *
     * <p>둘을 뺀다. `~세요`는 사용자에게 하는 권유라 주장이 아니고, 부정문은 이 렌즈의 정답이다 —
     * `사용자가 AI가 고친 index.html을 턴 2에서 안 부르셨어요`에서 그 경로는 `<changed_file>`에서
     * 온 정당한 근거이고, 문장이 말하는 것은 사용자가 그것을 말하지 **않았다**는 사실이다.
     */
    private static final Pattern USER_CLAIM = Pattern.compile("(셨어요|하셨|짚으셨)");
    private static final Pattern NEGATED = Pattern.compile("(안|못)\\s|없");
    private static final Pattern BACKTICKED = Pattern.compile("`([^`]+)`");

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
            case "B" -> runSignalPhase(reps);
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
        OpenAiChatModel chatModel = chatModel();
        List<CallRecord> records = new ArrayList<>();

        for (int rep = 1; rep <= reps; rep++) {
            for (Session session : LiveSessions.all()) {
                int currentRep = rep;
                Future<CallRecord> promptCall = executor.submit(
                        () -> call(phase, currentRep, session, Lens.PROMPT, chatModel));
                Future<CallRecord> patternCall = executor.submit(
                        () -> call(phase, currentRep, session, Lens.PATTERN, chatModel));

                records.add(await(promptCall));
                records.add(await(patternCall));
            }
        }

        return records;
    }

    /**
     * B 페이즈는 프롬프트 렌즈만 반복한다 — 채점 결과를 싣는 것은 그 렌즈뿐이다. pattern 렌즈는 첫 회에
     * 다섯 세션만 무신호로 돌려 어휘와 계약이 무너지지 않았는지 회귀로 확인한다.
     */
    private List<CallRecord> runSignalPhase(int reps) {
        OpenAiChatModel chatModel = chatModel();
        List<CallRecord> records = new ArrayList<>();

        for (int rep = 1; rep <= reps; rep++) {
            for (Session session : LiveSessions.all()) {
                int currentRep = rep;
                Future<CallRecord> promptCall = executor.submit(
                        () -> call("B", currentRep, session, Lens.PROMPT, session.testResults(), chatModel));

                if (rep > 1) {
                    records.add(await(promptCall));
                    continue;
                }

                Future<CallRecord> patternCall = executor.submit(
                        () -> call("B", currentRep, session, Lens.PATTERN, TurnTestResults.EMPTY, chatModel));

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
    private CallRecord call(String phase, int rep, Session session, Lens lens, OpenAiChatModel chatModel) {
        return call(phase, rep, session, lens, TurnTestResults.EMPTY, chatModel);
    }

    private CallRecord call(
            String phase,
            int rep,
            Session session,
            Lens lens,
            TurnTestResults testResults,
            OpenAiChatModel chatModel
    ) {
        RecordingChatModel recorder = new RecordingChatModel(chatModel);
        OpenAiFeedbackGenerator generator = generatorFor(lens, ChatClient.builder(recorder));
        long startedAt = System.nanoTime();

        try {
            FeedbackDraft draft = generator.generate(session.problem(), session.attempt(), testResults);

            return succeeded(
                    phase,
                    rep,
                    session,
                    lens,
                    draft,
                    quotesOf(lens, session, testResults, recorder),
                    startedAt);
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
            QuoteMetrics quotes,
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

        PatternMetrics pattern = lens == Lens.PATTERN
                ? patternMetrics(session, draft)
                : PatternMetrics.NONE;

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
                lens == Lens.PROMPT && lastTurnSentencePresent(draft.turnFeedbacks()),
                lens == Lens.PROMPT ? scoreLeaks(draft) : 0,
                quotes.total(),
                quotes.rawMiss(),
                quotes.normalizedMiss(),
                quotes.turnScopedMiss(),
                pattern.names(),
                pattern.techniques(),
                pattern.nameScored(),
                pattern.nameHits(),
                pattern.techniqueScored(),
                pattern.techniqueHits(),
                pattern.techniqueOnNonVibe(),
                pattern.attributionMisses());
    }

    /**
     * pattern 렌즈만의 지표. 이름과 기법은 절 제목 바로 뒤 첫 줄이 용어로 시작한다는 계약에 기댄다 —
     * 그 계약이 깨지면 용어가 아닌 것으로 잡혀 불일치로 남고, 그게 정확히 우리가 알고 싶은 것이다.
     */
    private PatternMetrics patternMetrics(Session session, FeedbackDraft draft) {
        List<String> names = new ArrayList<>();
        List<String> techniques = new ArrayList<>();
        int nameScored = 0;
        int nameHits = 0;
        int techniqueScored = 0;
        int techniqueHits = 0;
        int techniqueOnNonVibe = 0;

        for (int index = 0; index < draft.turnFeedbacks().size(); index++) {
            String feedback = draft.turnFeedbacks().get(index);
            String name = termAfter(PATTERN_NAME, feedback);
            String technique = termAfter(PATTERN_TECHNIQUE, feedback);
            names.add(name);
            techniques.add(technique);

            if (technique != null && !"vibe coding".equals(name)) {
                techniqueOnNonVibe++;
            }

            LiveSessions.PatternExpected expectation = index < session.patternTurns().size()
                    ? session.patternTurns().get(index)
                    : LiveSessions.PatternExpected.unscored();

            if (expectation.name() != null) {
                nameScored++;

                if (expectation.name().equals(name)) {
                    nameHits++;
                }
            }

            if (expectation.technique() != null) {
                techniqueScored++;
                String actual = technique == null ? LiveSessions.PatternExpected.NONE : technique;

                if (expectation.technique().equals(actual)) {
                    techniqueHits++;
                }
            }
        }

        return new PatternMetrics(names, techniques, nameScored, nameHits, techniqueScored,
                techniqueHits, techniqueOnNonVibe, attributionMisses(session, draft));
    }

    /**
     * 사용자가 한 적 없는 말을 사용자 발언·행위로 적은 문장을 센다. 백틱으로 감싼 이름만 본다 —
     * 파일 경로와 코드 식별자가 거기 들어가고, 지어내기가 실제로 난 자리가 그곳이다.
     *
     * <p>모델이 `<changed_file>`에서 가져온 이름을 AI를 주어로 쓰는 것은 정당하므로 세지 않는다.
     */
    private int attributionMisses(Session session, FeedbackDraft draft) {
        StringBuilder prompts = new StringBuilder();

        for (AttemptView.TurnView turn : session.attempt().turns()) {
            prompts.append(turn.userPrompt()).append("\n");
        }

        String userText = prompts.toString();
        List<String> texts = new ArrayList<>(draft.turnFeedbacks());
        texts.add(draft.overall());
        int misses = 0;

        for (String text : texts) {
            if (text == null) {
                continue;
            }

            for (String sentence : text.split("(?<=[.요])\\s+")) {
                if (!USER_CLAIM.matcher(sentence).find()
                        || sentence.contains("세요")
                        || NEGATED.matcher(sentence).find()) {
                    continue;
                }

                Matcher matcher = BACKTICKED.matcher(sentence);

                while (matcher.find()) {
                    if (!userText.contains(matcher.group(1))) {
                        System.out.printf(
                                "[harness] %s 사용자 오귀속 | name=%s | 문장=%s%n",
                                session.name(), matcher.group(1), LogFormats.abbreviate(sentence.trim()));
                        misses++;
                    }
                }
            }
        }

        return misses;
    }

    /**
     * 절 제목 뒤 첫 줄이 어느 사전 용어로 시작하는지. 절이 없거나 용어로 시작하지 않으면 null이다.
     */
    private String termAfter(Pattern section, String feedback) {
        if (feedback == null) {
            return null;
        }

        Matcher matcher = section.matcher(feedback);

        if (!matcher.find()) {
            return null;
        }

        String line = matcher.group(1).trim();

        for (String term : TERMS) {
            if (line.startsWith(term)) {
                return term;
            }
        }

        return null;
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
                false,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                0,
                0,
                0,
                0,
                0,
                0);
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

    /**
     * 사용자에게 나가는 문장에 채점 숫자가 섞였는지 센다. 총평까지 함께 본다.
     */
    private int scoreLeaks(FeedbackDraft draft) {
        int leaks = 0;
        List<String> texts = new ArrayList<>(draft.turnFeedbacks());
        texts.add(draft.overall());

        for (String text : texts) {
            if (text == null) {
                continue;
            }

            Matcher matcher = SCORE_LEAK.matcher(text);

            while (matcher.find()) {
                System.out.printf("[harness] 채점 문장 누출 | match=%s%n", matcher.group());
                leaks++;
            }
        }

        return leaks;
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

    /**
     * 인용 대조는 하네스가 직접 한다. 생성기는 대조를 마치고 인용을 버리므로 원본 응답을 되읽어야
     * raw·정규화·턴스코프 세 수준을 각각 셀 수 있다.
     *
     * <p>페이즈 0의 응답에는 quotes 필드가 없다 — 그때는 총 개수가 0이고 지표도 0이다.
     */
    private QuoteMetrics quotesOf(
            Lens lens,
            Session session,
            TurnTestResults testResults,
            RecordingChatModel recorder
    ) {
        String content = recorder.last();

        if (content == null || content.isBlank()) {
            return QuoteMetrics.NONE;
        }

        List<OpenAiFeedbackGenerator.TurnEntry> entries = new ArrayList<>();

        try {
            JsonNode turnFeedbacks = objectMapper.readTree(content).path("turnFeedbacks");

            for (JsonNode turn : turnFeedbacks) {
                List<String> quotes = new ArrayList<>();

                for (JsonNode quote : turn.path("quotes")) {
                    quotes.add(quote.asText());
                }

                entries.add(new OpenAiFeedbackGenerator.TurnEntry(quotes, turn.path("feedback").asText()));
            }
        } catch (IOException exception) {
            return QuoteMetrics.NONE;
        }

        QuoteVerifier.Result result =
                QuoteVerifier.verify(userMessageOf(lens, session, testResults), entries);

        for (QuoteVerifier.Miss miss : result.normalizedMisses()) {
            System.out.printf(
                    "[harness] %s %s 인용 불일치 | turn=%d | quote=%s%n",
                    session.name(), lens.label, miss.turn(), LogFormats.abbreviate(miss.quote()));
        }

        return new QuoteMetrics(
                result.total(),
                result.rawMisses().size(),
                result.normalizedMisses().size(),
                result.turnScopedMisses().size());
    }

    private String userMessageOf(Lens lens, Session session, TurnTestResults testResults) {
        if (lens == Lens.PROMPT) {
            return FeedbackPrompts.userPrompt(session.problem(), session.attempt(), testResults);
        }

        return PatternPrompts.userPrompt(session.problem(), session.attempt());
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
                (problem, attempt, testResults) -> PatternPrompts.userPrompt(problem, attempt),
                optionsFactory::forPatternFeedback);
    }

    /**
     * 운영과 같은 경로로 조립한다. spring-ai 2.0.0의 OpenAI 클라이언트는 {@code OpenAiSetup}이 만든다 —
     * 이 배포에는 {@code openai-java-client-okhttp}가 없고 spring-ai가 자기 okhttp 클라이언트를 쓴다.
     */
    private OpenAiChatModel chatModel() {
        return OpenAiChatModel.builder()
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
        int quotes = 0;
        int rawMiss = 0;
        int normalizedMiss = 0;
        int turnScopedMiss = 0;
        int callsWithMiss = 0;
        int leaks = 0;
        int callsWithLeak = 0;
        int promptCalls = 0;

        for (CallRecord record : records) {
            if (record.ok()) {
                ok++;
            }

            scored += record.judgementScored();
            hits += record.judgementHits();
            decisiveScored += record.decisiveScored();
            decisiveHits += record.decisiveHits();
            tokens += record.completionTokens() == null ? 0 : record.completionTokens();
            quotes += record.quoteTotal();
            rawMiss += record.rawMiss();
            normalizedMiss += record.normalizedMiss();
            turnScopedMiss += record.turnScopedMiss();

            if (record.normalizedMiss() > 0) {
                callsWithMiss++;
            }

            if ("prompt".equals(record.lens())) {
                promptCalls++;
                leaks += record.scoreLeaks();

                if (record.scoreLeaks() > 0) {
                    callsWithLeak++;
                }
            }
        }

        System.out.println("[harness] ===== phase " + phase + " =====");
        System.out.printf("[harness] 계약 성공 %d/%d (%.1f%%)%n", ok, calls, percent(ok, calls));
        System.out.printf("[harness] 판정1 정확도 %d/%d (%.1f%%)%n", hits, scored, percent(hits, scored));
        System.out.printf("[harness] 결정적 턴 판정1 %d/%d%n", decisiveHits, decisiveScored);
        System.out.printf(
                "[harness] 인용 %d개 | raw 불일치 %d | 정규화 불일치 %d (일치율 %.1f%%) | 턴스코프 불일치 %d%n",
                quotes, rawMiss, normalizedMiss, percent(quotes - normalizedMiss, quotes), turnScopedMiss);
        System.out.printf("[harness] 불일치 포함 호출 %d/%d%n", callsWithMiss, calls);
        System.out.printf(
                "[harness] 채점 문장 누출 %d건 | 누출 포함 호출 %d/%d (프롬프트 렌즈)%n",
                leaks, callsWithLeak, promptCalls);
        summarizePattern(records);
        System.out.printf("[harness] 완성 토큰 합계 %d%n", tokens);
    }

    /**
     * pattern 렌즈만 따로 낸다. 프롬프트 렌즈와 섞으면 분모가 달라 읽을 수 없다.
     */
    private void summarizePattern(List<CallRecord> records) {
        int nameScored = 0;
        int nameHits = 0;
        int techniqueScored = 0;
        int techniqueHits = 0;
        int onNonVibe = 0;
        int attribution = 0;
        var names = new java.util.TreeMap<String, Integer>();
        var techniques = new java.util.TreeMap<String, Integer>();

        for (CallRecord record : records) {
            if (!"pattern".equals(record.lens())) {
                continue;
            }

            nameScored += record.patternNameScored();
            nameHits += record.patternNameHits();
            techniqueScored += record.techniqueScored();
            techniqueHits += record.techniqueHits();
            onNonVibe += record.techniqueOnNonVibe();
            attribution += record.attributionMisses();
            record.patternNames().forEach(
                    name -> names.merge(name == null ? "(이름없음)" : name, 1, Integer::sum));
            record.patternTechniques().stream().filter(java.util.Objects::nonNull).forEach(
                    technique -> techniques.merge(technique, 1, Integer::sum));
        }

        System.out.printf(
                "[harness] pattern 이름 정확도 %d/%d (%.1f%%) | 분포 %s%n",
                nameHits, nameScored, percent(nameHits, nameScored), names);
        System.out.printf(
                "[harness] pattern 기법 정확도 %d/%d (%.1f%%) | 분포 %s%n",
                techniqueHits, techniqueScored, percent(techniqueHits, techniqueScored), techniques);
        System.out.printf(
                "[harness] vibe coding 아닌 턴에 붙은 기법 %d | 사용자 오귀속 %d%n", onNonVibe, attribution);
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
     * 실제 모델 앞에 끼워 원본 응답 본문을 붙잡는다. 생성기가 인용을 버리고 draft만 돌려주므로,
     * 인용 지표는 여기 남은 마지막 응답에서 되읽는다(재시도가 있으면 draft를 만든 쪽이 마지막이다).
     */
    private static final class RecordingChatModel implements ChatModel {

        private final ChatModel delegate;
        private final List<String> responses = new CopyOnWriteArrayList<>();

        private RecordingChatModel(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatOptions getOptions() {
            return delegate.getOptions();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            ChatResponse response = delegate.call(prompt);
            Generation result = response == null ? null : response.getResult();

            if (result != null && result.getOutput() != null) {
                responses.add(result.getOutput().getText());
            }

            return response;
        }

        private String last() {
            return responses.isEmpty() ? null : responses.getLast();
        }
    }

    /**
     * 인용 대조 지표. 계약으로 쓰는 것은 {@code normalizedMiss} 하나고 나머지 둘은 진단이다.
     */
    record QuoteMetrics(int total, int rawMiss, int normalizedMiss, int turnScopedMiss) {

        static final QuoteMetrics NONE = new QuoteMetrics(0, 0, 0, 0);
    }

    /**
     * pattern 렌즈 지표. 이 렌즈는 지금까지 계약 성공 여부만 재고 이름은 한 번도 채점하지 않았다.
     *
     * @param techniqueOnNonVibe `vibe coding`이 아닌 턴에 처방이 붙은 수. 잘한 턴에 기법을 주면 잔소리가 된다
     * @param attributionMisses  사용자가 한 적 없는 말을 사용자 발언·행위로 적은 수
     */
    record PatternMetrics(
            List<String> names,
            List<String> techniques,
            int nameScored,
            int nameHits,
            int techniqueScored,
            int techniqueHits,
            int techniqueOnNonVibe,
            int attributionMisses
    ) {

        static final PatternMetrics NONE =
                new PatternMetrics(List.of(), List.of(), 0, 0, 0, 0, 0, 0);
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
            boolean lastTurnSentence,
            int scoreLeaks,
            int quoteTotal,
            int rawMiss,
            int normalizedMiss,
            int turnScopedMiss,
            List<String> patternNames,
            List<String> patternTechniques,
            int patternNameScored,
            int patternNameHits,
            int techniqueScored,
            int techniqueHits,
            int techniqueOnNonVibe,
            int attributionMisses
    ) {
    }
}
