package com.promptstudio.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptstudio.ai.CarryLineChecks.EditAudit;
import com.promptstudio.ai.CarryLineChecks.Verdict;
import com.promptstudio.ai.CarryLineSessions.Run;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.problem.domain.ProblemFile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 상시 지시 한 줄(carry line)이 코드 생성 툴 루프에서 실제로 지켜지는지를 재는 하네스.
 *
 * <p><b>유닛 테스트가 아니라 측정 도구다.</b> 준수율이 얼마가 나오든 실패시키지 않는다 —
 * 숫자와 원문만 남기고 합격선 판정은 사람이 한다. 여기서 죽는 유일한 경우는 <b>설정 오류</b>,
 * 즉 알 수 없는 {@code -PllmPhase}다.
 *
 * <p>{@code llm} 태그가 붙어 있어 {@code test} 태스크에서는 제외되고 {@code llmTest}에서만 돈다.
 *
 * <pre>
 * set -a; . ../.env; set +a
 * sh gradlew llmTest --tests "*LiveCarryLineHarnessTest" -PllmPhase=all -PllmReps=1
 * </pre>
 *
 * <p>{@link OpenAiCodeGenerator}의 툴 루프를 <b>미러</b>한다(호출하지 않는다). 어댑터는
 * {@code ProblemView}/{@code AttemptView}를 받고 시스템 프롬프트를 손댈 자리를 주지 않는데,
 * 이 실험이 흔드는 것이 정확히 그 시스템 프롬프트라서 루프를 여기 다시 세웠다. 라운드 상한·강제
 * 마무리 문구·대체 요약은 어댑터의 값을 그대로 복제한다 — 어느 한쪽이 바뀌면 여기도 바꿔야 한다.
 *
 * <p>설계 근거는 {@code backend/docs/carry-line-design.md}에 있다.
 */
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class LiveCarryLineHarnessTest {

    private static final String DEFAULT_MODEL = "gpt-5.6-luna";
    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(6);
    private static final int MAX_RETRIES = 2;
    private static final String LANGUAGE = "java";

    /** {@link OpenAiCodeGenerator}의 복제본. 어댑터가 바뀌면 여기도 바꿔야 한다. */
    private static final int MAX_TOOL_ROUNDS = 10;
    private static final String FALLBACK_SUMMARY = "작업을 완료했지만 AI가 요약을 제공하지 않았습니다.";
    private static final String FORCED_FINALIZE_PROMPT =
            "툴 사용 한도에 도달했습니다. 지금까지 수행한 작업을 한국어로 요약해 주세요.";

    /** 문안을 붙이는 자리. 대조군은 이 절이 통째로 없다. */
    private static final String RULE_SECTION = "\n# 사용자 상시 지시\n%s\n";

    /** 이 라운드부터 후반으로 센다. 라운드 깊이 유지(게이트 2)의 경계다. */
    private static final int LATE_ROUND_FROM = 3;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String model = envOrDefault("OPENAI_CODE_MODEL", DEFAULT_MODEL);
    private final OpenAiChatOptionsFactory optionsFactory =
            new OpenAiChatOptionsFactory(model, model, model);

    @Test
    void 상시_지시_한_줄의_준수율을_잰다() throws Exception {
        String phase = System.getProperty("llmPhase", "0");
        int reps = Integer.parseInt(System.getProperty("llmReps", "1"));

        // 설정 오류는 여기서 죽는다. 측정값으로는 죽지 않는다.
        List<Run> runs = CarryLineSessions.runsFor(phase);

        System.out.printf("[carry] phase=%s reps=%d model=%s%n", phase, reps, model);
        System.out.printf("[carry] 실행 %d개 × reps %d = 턴 실행 %d회%n",
                runs.size(), reps, runs.stream().mapToInt(run -> run.session().prompts().size()).sum() * reps);

        OpenAiChatModel chatModel = chatModel();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        List<TurnRecord> records = new ArrayList<>();

        // 전 실행 순차. 한꺼번에 던지면 레이트 리밋 실패가 준수 실패로 잘못 잡힌다.
        for (int rep = 1; rep <= reps; rep++) {
            for (Run run : runs) {
                records.addAll(runSession(chatModel, toolCallingManager, phase, run, rep));
            }
        }

        write(phase, records);
        summarize(phase, records);
    }

    /**
     * 세션 하나를 턴 순서대로 돈다. 턴 K의 작업본이 턴 K+1의 입력이다 — 프로덕션의 파일 재생과
     * 같은 결과를 손으로 만든다.
     */
    private List<TurnRecord> runSession(
            OpenAiChatModel chatModel, ToolCallingManager manager, String phase, Run run, int rep) {
        String systemPrompt = systemPromptFor(run.rule());
        List<ProblemFile> files = CarryLineSessions.skeleton();
        List<TurnRecord> records = new ArrayList<>();

        for (int turn = 1; turn <= run.session().prompts().size(); turn++) {
            TurnOutcome outcome = runTurn(
                    chatModel, manager, phase, run, rep, turn, systemPrompt,
                    run.session().prompts().get(turn - 1), files);

            records.add(outcome.record());
            files = outcome.files();

            System.out.printf(
                    "[carry] %s/%s rep=%d turn=%d | rounds=%d | 편집 %d | D1=%s D5=%s%s%n",
                    run.arm(), run.session().name(), rep, turn,
                    outcome.record().rounds(), outcome.record().editedPaths().size(),
                    outcome.record().summaryNamesEveryEditedFile(), outcome.record().howToCheck(),
                    outcome.record().ok() ? "" : " | 실패 " + outcome.record().failure());
        }

        return records;
    }

    /**
     * 턴 하나 = {@link OpenAiCodeGenerator#generate} 한 번에 대응한다.
     *
     * <p>호출이 실패해도 던지지 않는다 — 실패 사유를 기록에 싣고 다음 턴으로 넘어간다. 48회짜리
     * 실행이 429 하나로 통째로 죽으면 그때까지의 측정도 함께 사라진다.
     */
    private TurnOutcome runTurn(
            OpenAiChatModel chatModel,
            ToolCallingManager manager,
            String phase,
            Run run,
            int rep,
            int turn,
            String systemPrompt,
            String userPrompt,
            List<ProblemFile> files
    ) {
        CodeGenerationTools tools = new CodeGenerationTools(files, LANGUAGE);
        OpenAiChatOptions toolOptions =
                optionsFactory.forCodeGeneration("carry-" + run.arm(), tools.callbacks());
        Prompt prompt = new Prompt(
                List.of(new SystemMessage(systemPrompt), new UserMessage("[사용자 요청]\n" + userPrompt)),
                toolOptions);

        LlmUsageTracker tracker = new LlmUsageTracker();
        List<CarryLineChecks.Event> events = new ArrayList<>();
        List<RoundText> roundTexts = new ArrayList<>();
        List<TracedCall> toolTrace = new ArrayList<>();
        long startedAt = System.nanoTime();

        int rounds = 0;
        int traced = 0;
        boolean finalizeForced = false;
        String summary = null;
        String failure = null;

        try {
            for (int round = 1; round <= MAX_TOOL_ROUNDS && summary == null; round++) {
                rounds = round;
                ChatResponse response = callRound(chatModel, prompt, toolOptions.getModel(), tracker);
                String text = textOf(response);

                if (text != null && !text.isBlank()) {
                    // 같은 메시지에서 모델이 말을 먼저 하고 툴을 부른다. 텍스트가 툴 이벤트보다 앞선다.
                    events.add(new CarryLineChecks.Event(round, text, null, null));
                    roundTexts.add(new RoundText(round, text));
                }

                if (!response.hasToolCalls()) {
                    summary = text == null || text.isBlank() ? FALLBACK_SUMMARY : text.trim();
                    continue;
                }

                ToolExecutionResult executed = manager.executeToolCalls(prompt, response);
                traced = recordTrace(tools, traced, round, events, toolTrace);
                prompt = new Prompt(executed.conversationHistory(), toolOptions);
            }

            if (summary == null) {
                finalizeForced = true;
                summary = finalizeWithoutTools(
                        chatModel, prompt.getInstructions(), run.arm(), tracker, roundTexts);
            }
        } catch (RuntimeException exception) {
            failure = exception.getClass().getSimpleName();
            System.out.printf("[carry] %s/%s rep=%d turn=%d 호출 실패 | reason=%s%n",
                    run.arm(), run.session().name(), rep, turn, failure);
        }

        List<ProblemFile> after = tools.currentFiles();
        List<String> editedPaths = changedPaths(files, after);
        List<EditAudit> edits = CarryLineChecks.auditEdits(events, userPrompt);

        TurnRecord record = new TurnRecord(
                phase,
                run.arm(),
                run.rule(),
                run.session().name(),
                rep,
                turn,
                model,
                failure == null,
                failure,
                rounds,
                finalizeForced,
                FALLBACK_SUMMARY.equals(summary) ? "fallback" : "model",
                userPrompt,
                summary,
                editedPaths,
                edits,
                CarryLineChecks.summaryNamesEveryEditedFile(editedPaths, summary),
                CarryLineChecks.summaryStatesHowToCheck(summary),
                roundTexts,
                toolTrace,
                completionTokensOf(tracker.snapshot()),
                tracker.snapshot().size(),
                elapsedMillis(startedAt));

        return new TurnOutcome(record, after);
    }

    private ChatResponse callRound(
            OpenAiChatModel chatModel, Prompt prompt, String callModel, LlmUsageTracker tracker) {
        long startedAt = System.nanoTime();
        ChatResponse response = chatModel.call(prompt);

        tracker.record(callModel, response, elapsedMillis(startedAt));

        return response;
    }

    /**
     * 라운드 상한에 닿았을 때의 툴 없는 마무리 호출. 어댑터와 같이 실패해도 편집을 버리지 않고
     * 대체 요약으로 턴을 남긴다.
     */
    private String finalizeWithoutTools(
            OpenAiChatModel chatModel,
            List<Message> history,
            String arm,
            LlmUsageTracker tracker,
            List<RoundText> roundTexts
    ) {
        List<Message> messages = new ArrayList<>(history);
        messages.add(new UserMessage(FORCED_FINALIZE_PROMPT));
        OpenAiChatOptions options = optionsFactory.forCodeGenerationFinalize("carry-" + arm);

        try {
            long startedAt = System.nanoTime();
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            tracker.record(options.getModel(), response, elapsedMillis(startedAt));

            String text = textOf(response);

            if (text == null || text.isBlank()) {
                return FALLBACK_SUMMARY;
            }

            roundTexts.add(new RoundText(MAX_TOOL_ROUNDS + 1, text));

            return text.trim();
        } catch (RuntimeException exception) {
            System.out.printf("[carry] 마무리 호출 실패 | arm=%s | reason=%s%n",
                    arm, exception.getClass().getSimpleName());

            return FALLBACK_SUMMARY;
        }
    }

    /**
     * 이번 라운드에 새로 쌓인 툴콜을 이벤트로 옮긴다. 경로 파싱은 어댑터가 이미 한 것을 그대로 쓴다 —
     * 여기서 인자 JSON을 다시 읽으면 파서가 둘이 되고 한쪽만 고쳐질 수 있다.
     */
    private int recordTrace(
            CodeGenerationTools tools,
            int from,
            int round,
            List<CarryLineChecks.Event> events,
            List<TracedCall> toolTrace
    ) {
        List<ToolCallEntry> trace = tools.trace();

        for (int index = from; index < trace.size(); index++) {
            ToolCallEntry entry = trace.get(index);
            events.add(new CarryLineChecks.Event(round, null, entry.tool(), entry.path()));
            toolTrace.add(new TracedCall(round, entry.tool(), entry.path()));
        }

        return trace.size();
    }

    /**
     * 내용이 실제로 바뀐 경로. D1의 분모다.
     *
     * <p>툴 트레이스의 {@code edit_file}과 일부러 갈라 둔다 — 미존재 경로나 정책 위반으로 반영되지
     * 않은 편집이 D1 분모에 섞이면 "요약이 바꾸지도 않은 파일을 안 불렀다"가 위반으로 잡힌다.
     */
    private List<String> changedPaths(List<ProblemFile> before, List<ProblemFile> after) {
        Map<String, String> previous = new LinkedHashMap<>();

        for (ProblemFile file : before) {
            previous.put(file.path(), file.content());
        }

        List<String> changed = new ArrayList<>();

        for (ProblemFile file : after) {
            if (!file.content().equals(previous.get(file.path()))) {
                changed.add(file.path());
            }
        }

        return changed;
    }

    private String systemPromptFor(String rule) {
        String base = CodeGenerationPrompts.systemPrompt(LANGUAGE);

        return rule == null ? base : base + RULE_SECTION.formatted(rule);
    }

    private String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }

        AssistantMessage output = response.getResult().getOutput();

        return output == null ? null : output.getText();
    }

    /**
     * 운영과 같은 경로로 조립한다. spring-ai 2.0.0의 OpenAI 클라이언트는 {@code OpenAiSetup}이 만든다.
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

    /**
     * 원문 보존이 이 파일의 첫째 목적이다. 파서가 백틱을 누락으로 세는 바람에 지표가 80%에서 60%로
     * 잘못 보였고, 생성물을 안 보고 프롬프트를 고쳤다가 40%로 더 떨어뜨린 사고가 있었다. 지표는
     * 무엇이 빠졌는지만 알려 주고 왜인지는 못 말한다.
     */
    private void write(String phase, List<TurnRecord> records) throws IOException {
        Path directory = Path.of("build", "llm-harness");
        Files.createDirectories(directory);
        Path file = directory.resolve("carry-" + phase + ".jsonl");
        StringBuilder lines = new StringBuilder();

        for (TurnRecord record : records) {
            lines.append(objectMapper.writeValueAsString(record)).append("\n");
        }

        Files.writeString(file, lines.toString());
        System.out.printf("[carry] wrote %d records to %s%n", records.size(), file.toAbsolutePath());
    }

    private void summarize(String phase, List<TurnRecord> records) {
        Map<String, ArmStats> byArm = new LinkedHashMap<>();
        long tokens = 0;

        for (TurnRecord record : records) {
            byArm.computeIfAbsent(keyOf(record.arm(), record.session()), key -> new ArmStats()).add(record);
            tokens += record.completionTokens() == null ? 0 : record.completionTokens();
        }

        System.out.println("[carry] ===== phase " + phase + " =====");

        for (Map.Entry<String, ArmStats> entry : byArm.entrySet()) {
            ArmStats stats = entry.getValue();
            ArmStats control = byArm.get(keyOf(CarryLineSessions.CONTROL, sessionOf(entry.getKey())));
            boolean isControl = entry.getKey().startsWith(CarryLineSessions.CONTROL + "@");

            System.out.printf(
                    "[carry] %s | 턴 %d(실패 %d) | D1 %s%s | D2 %s%s | D3 %s%s | D4 %s%s | D5 %s%s (UNSCORED %d)%n",
                    entry.getKey(), stats.turns, stats.failedTurns,
                    stats.rate("D1"), delta(stats, control, "D1", isControl),
                    stats.rate("D2"), delta(stats, control, "D2", isControl),
                    stats.rate("D3"), delta(stats, control, "D3", isControl),
                    stats.rate("D4"), delta(stats, control, "D4", isControl),
                    stats.rate("D5"), delta(stats, control, "D5", isControl), stats.d5Unscored);

            System.out.printf(
                    "[carry]   라운드 구간 | D2 초반 %s 후반 %s | D3 초반 %s 후반 %s | D4 초반 %s 후반 %s%n",
                    stats.early("D2"), stats.late("D2"),
                    stats.early("D3"), stats.late("D3"),
                    stats.early("D4"), stats.late("D4"));

            System.out.printf(
                    "[carry]   요약 전 라운드 | ≤3 D1 %s D5 %s | ≥4 D1 %s D5 %s | 마무리 호출 %d턴 D1 %s D5 %s%n",
                    stats.shallow("D1"), stats.shallow("D5"),
                    stats.deep("D1"), stats.deep("D5"),
                    stats.finalizeTurns, stats.forced("D1"), stats.forced("D5"));

            System.out.printf(
                    "[carry]   편집 분포 | 편집 있는 턴 %d/%d | 편집 %d건 중 후반(%d+) %d건 (%.1f%%)%n",
                    stats.turns - stats.zeroEditTurns, stats.turns,
                    stats.edits, LATE_ROUND_FROM, stats.lateEdits, percent(stats.lateEdits, stats.edits));
        }

        System.out.printf("[carry] 완성 토큰 합계 %d%n", tokens);
        System.out.println("[carry] 판정은 사람이 한다 — jsonl의 요약 전문과 라운드 텍스트부터 읽을 것.");
    }

    /**
     * 같은 세션의 대조군과의 차이. 대조군 자신과 분모가 빈 칸은 찍지 않는다 — 0%p로 찍으면
     * "재 봤는데 차이가 없다"와 "잴 표본이 없다"가 같은 모양이 된다.
     */
    private String delta(ArmStats stats, ArmStats control, String key, boolean isControl) {
        if (isControl || control == null) {
            return "";
        }

        Rate mine = stats.rates.get(key);
        Rate theirs = control.rates.get(key);

        if (mine == null || theirs == null || mine.total == 0 || theirs.total == 0) {
            return "";
        }

        return " [Δ%+.1f%%p]".formatted(mine.percent() - theirs.percent());
    }

    private String keyOf(String arm, String session) {
        return arm + "@" + session;
    }

    private String sessionOf(String key) {
        return key.substring(key.indexOf('@') + 1);
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

    private static double percent(int part, int total) {
        return total == 0 ? 0 : part * 100.0 / total;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);

        return value == null || value.isBlank() ? fallback : value;
    }

    /** 턴 하나의 측정 결과와 다음 턴에 넘길 작업본. 작업본은 jsonl에 싣지 않는다 — 파일 전문이라 크다. */
    private record TurnOutcome(TurnRecord record, List<ProblemFile> files) {
    }

    private record RoundText(int round, String text) {
    }

    private record TracedCall(int round, String tool, String path) {
    }

    /**
     * jsonl 한 줄이 이 레코드 하나다 — 턴 하나.
     *
     * @param rule       이 팔이 붙인 상시 지시. 대조군은 null이다
     * @param ok         호출이 끝까지 갔는가. 측정 실패이지 준수 실패가 아니다
     * @param rounds     이 턴이 실제로 돈 툴 라운드 수
     * @param summarySource {@code model} 또는 {@code fallback}. 대체 요약은 D1·D5를 자연히 실패하므로
     *                      준수 실패와 갈라 읽어야 한다
     * @param editedPaths 내용이 실제로 바뀐 경로 (D1의 분모)
     * @param edits       {@code edit_file} 한 건마다의 감사 (D2·D3·D4, 라운드 번호 포함)
     * @param roundTexts  라운드별 어시스턴트 원문. 마무리 호출은 라운드 {@code MAX_TOOL_ROUNDS + 1}이다
     */
    record TurnRecord(
            String phase,
            String arm,
            String rule,
            String session,
            int rep,
            int turn,
            String model,
            boolean ok,
            String failure,
            int rounds,
            boolean finalizeForced,
            String summarySource,
            String userPrompt,
            String summary,
            List<String> editedPaths,
            List<EditAudit> edits,
            boolean summaryNamesEveryEditedFile,
            Verdict howToCheck,
            List<RoundText> roundTexts,
            List<TracedCall> toolTrace,
            Integer completionTokens,
            int llmCalls,
            long elapsedMs
    ) {
    }

    /** 통과/분모 한 쌍. 분모가 0인 칸을 0%로 찍지 않기 위해 둘을 함께 들고 다닌다. */
    private static final class Rate {

        private int pass;
        private int total;

        private void add(boolean ok) {
            total++;

            if (ok) {
                pass++;
            }
        }

        private double percent() {
            return LiveCarryLineHarnessTest.percent(pass, total);
        }

        @Override
        public String toString() {
            return total == 0 ? "-" : "%d/%d (%.1f%%)".formatted(pass, total, percent());
        }
    }

    /**
     * 팔 하나(세션 포함)의 집계. 검출기 이름으로 칸을 나눠 들고, 라운드 구간과 마무리 호출을 따로 센다.
     */
    private static final class ArmStats {

        private final Map<String, Rate> rates = new LinkedHashMap<>();
        private final Map<String, Rate> earlyRates = new LinkedHashMap<>();
        private final Map<String, Rate> lateRates = new LinkedHashMap<>();
        private final Map<String, Rate> shallowRates = new LinkedHashMap<>();
        private final Map<String, Rate> deepRates = new LinkedHashMap<>();
        private final Map<String, Rate> finalizeRates = new LinkedHashMap<>();

        private int turns;
        private int failedTurns;
        private int zeroEditTurns;
        private int edits;
        private int lateEdits;
        private int d5Unscored;
        private int finalizeTurns;

        private void add(TurnRecord record) {
            turns++;

            if (!record.ok()) {
                failedTurns++;
            }

            if (record.finalizeForced()) {
                finalizeTurns++;
            }

            addSummaryDetectors(record);
            addEditDetectors(record);
        }

        /**
         * D1은 편집이 있는 턴만 센다 — 편집 0회 턴을 넣으면 아무것도 안 고치고 "준수"하는 퇴화가
         * 준수율을 부풀린다. 그 턴 수는 따로 세어 함께 찍는다.
         */
        private void addSummaryDetectors(TurnRecord record) {
            if (record.editedPaths().isEmpty()) {
                zeroEditTurns++;
            } else {
                bucket(rates, "D1").add(record.summaryNamesEveryEditedFile());
                bucket(record.rounds() >= 4 ? deepRates : shallowRates, "D1")
                        .add(record.summaryNamesEveryEditedFile());

                if (record.finalizeForced()) {
                    bucket(finalizeRates, "D1").add(record.summaryNamesEveryEditedFile());
                }
            }

            if (record.howToCheck() == Verdict.UNSCORED) {
                d5Unscored++;
                return;
            }

            boolean passed = record.howToCheck() == Verdict.PASS;
            bucket(rates, "D5").add(passed);
            bucket(record.rounds() >= 4 ? deepRates : shallowRates, "D5").add(passed);

            if (record.finalizeForced()) {
                bucket(finalizeRates, "D5").add(passed);
            }
        }

        private void addEditDetectors(TurnRecord record) {
            for (EditAudit audit : record.edits()) {
                edits++;
                boolean late = audit.round() >= LATE_ROUND_FROM;

                if (late) {
                    lateEdits++;
                }

                add("D2", audit.announced(), late);
                add("D3", audit.withinPrompt(), late);
                add("D4", audit.readFirst(), late);
            }
        }

        private void add(String key, boolean ok, boolean late) {
            bucket(rates, key).add(ok);
            bucket(late ? lateRates : earlyRates, key).add(ok);
        }

        private static Rate bucket(Map<String, Rate> map, String key) {
            return map.computeIfAbsent(key, name -> new Rate());
        }

        private String rate(String key) {
            return String.valueOf(bucket(rates, key));
        }

        private String early(String key) {
            return String.valueOf(bucket(earlyRates, key));
        }

        private String late(String key) {
            return String.valueOf(bucket(lateRates, key));
        }

        private String shallow(String key) {
            return String.valueOf(bucket(shallowRates, key));
        }

        private String deep(String key) {
            return String.valueOf(bucket(deepRates, key));
        }

        private String forced(String key) {
            return String.valueOf(bucket(finalizeRates, key));
        }
    }
}
