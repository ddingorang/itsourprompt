package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptLlmCall;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.domain.LlmCallPurpose;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.domain.LlmUsageSummary;
import com.promptstudio.attempt.domain.LlmUsageTotals;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptQueryRepositoryTest extends DatabaseTest {

    private static final ProblemFile SKELETON = new ProblemFile("src/Main.java", "class Main {}");

    @Autowired
    private AttemptQueryRepository attemptQueryRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private LlmCallRepository llmCallRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Test
    void 저장한_어템프트를_조회한다() {
        Attempt saved = attemptRepository.save(Attempt.start(newProblem(), ownerId));

        AttemptView view = attemptQueryRepository.findById(saved.id()).orElseThrow();

        assertThat(view.id()).isEqualTo(saved.id());
        assertThat(view.problemId()).isEqualTo(saved.problemId());
        assertThat(view.baseFiles()).containsExactly(SKELETON);
        assertThat(view.files()).containsExactly(SKELETON);
        assertThat(view.turns()).isEmpty();
    }

    @Test
    void 시작_스켈레톤은_보존하고_현재_파일은_턴을_재생해_돌려준다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));
        attempt.applyTurn(
                "Main을 채워줘",
                new GeneratedCode(
                        List.of(new ProblemFile("src/Main.java", "생성된 내용")), "첫 요약", List.of(), List.of())
        );
        attempt.applyTurn(
                "Main은 지우고 Util만 남겨줘",
                new GeneratedCode(
                        List.of(new ProblemFile("src/Util.java", "class Util {}")), "둘째 요약", List.of(), List.of())
        );
        attemptRepository.save(attempt);

        AttemptView view = attemptQueryRepository.findById(attempt.id()).orElseThrow();

        assertThat(view.baseFiles()).containsExactly(SKELETON);
        assertThat(view.files()).containsExactly(new ProblemFile("src/Util.java", "class Util {}"));
    }

    @Test
    void 턴_순서와_변경파일_순서를_보존한다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));
        attempt.applyTurn(
                "Main을 채워줘",
                new GeneratedCode(
                        List.of(new ProblemFile("src/Main.java", "생성된 내용")),
                        "첫 요약",
                        List.of(new ToolCallEntry("list_files", null), new ToolCallEntry("edit_file", "src/Main.java")),
                        List.of()
                )
        );
        attempt.applyTurn(
                "Util도 만들어줘",
                new GeneratedCode(
                        List.of(
                                new ProblemFile("src/Main.java", "생성된 내용"),
                                new ProblemFile("src/Util.java", "class Util {}")
                        ),
                        "둘째 요약",
                        List.of(new ToolCallEntry("edit_file", "src/Util.java")),
                        List.of()
                )
        );
        attemptRepository.save(attempt);

        AttemptView view = attemptQueryRepository.findById(attempt.id()).orElseThrow();

        assertThat(view.turns()).hasSize(2);
        assertThat(view.turns().get(0).userPrompt()).isEqualTo("Main을 채워줘");
        assertThat(view.turns().get(0).aiSummary()).isEqualTo("첫 요약");
        assertThat(view.turns().get(0).changes())
                .containsExactly(new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED, "생성된 내용"));
        assertThat(view.turns().get(0).toolCalls()).containsExactly(
                new ToolCallEntry("list_files", null),
                new ToolCallEntry("edit_file", "src/Main.java")
        );
        assertThat(view.turns().get(1).userPrompt()).isEqualTo("Util도 만들어줘");
        assertThat(view.turns().get(1).changes())
                .containsExactly(new FileChange("src/Util.java", FileChange.ChangeType.ADDED, "class Util {}"));
        assertThat(view.turns().get(1).toolCalls())
                .containsExactly(new ToolCallEntry("edit_file", "src/Util.java"));
    }

    @Test
    void 삭제된_변경_파일은_코드가_비어_있다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));
        attempt.applyTurn(
                "Main을 지워줘",
                new GeneratedCode(
                        List.of(new ProblemFile("src/Util.java", "class Util {}")), "요약", List.of(), List.of())
        );
        attemptRepository.save(attempt);

        AttemptView view = attemptQueryRepository.findById(attempt.id()).orElseThrow();

        assertThat(view.turns().getFirst().changes()).containsExactly(
                new FileChange("src/Main.java", FileChange.ChangeType.DELETED, null),
                new FileChange("src/Util.java", FileChange.ChangeType.ADDED, "class Util {}")
        );
    }

    @Test
    void 상태와_피드백을_함께_조회한다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));

        AttemptView started = attemptQueryRepository.findById(attempt.id()).orElseThrow();

        assertThat(started.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(started.feedback()).isNull();

        attempt.submit(new AttemptFeedback(List.of(), "저장된 피드백", List.of()));
        attemptRepository.save(attempt);

        AttemptView submitted = attemptQueryRepository.findById(attempt.id()).orElseThrow();

        assertThat(submitted.status()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(submitted.feedback()).isEqualTo("저장된 피드백");
    }

    @Test
    void 없는_ID면_빈_Optional을_반환한다() {
        assertThat(attemptQueryRepository.findById(9999L)).isEmpty();
    }

    @Test
    void 턴별_사용량_합계와_어템프트_총계를_파생한다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));
        attempt.applyTurn("첫 턴", generated("첫 내용", "첫 요약"));
        attempt.applyTurn("둘째 턴", generated("둘째 내용", "둘째 요약"));
        attemptRepository.save(attempt);

        llmCallRepository.saveAll(List.of(
                codeCall(attempt.id(), 0, usage(1, 1_000L, 200L, 400L, 50L, 120L), "0.00120000"),
                codeCall(attempt.id(), 0, usage(2, 1_500L, 300L, 600L, 70L, 180L), "0.00180000"),
                codeCall(attempt.id(), 1, usage(1, 800L, 100L, 200L, 30L, 90L), "0.00090000")
        ));

        AttemptView view = attemptQueryRepository.findById(attempt.id()).orElseThrow();

        LlmUsageSummary first = view.turns().get(0).usage();
        assertThat(first.inputTokens()).isEqualTo(2_500L);
        assertThat(first.uncachedInputTokens()).isEqualTo(1_500L);
        assertThat(first.cachedInputTokens()).isEqualTo(1_000L);
        assertThat(first.outputTokens()).isEqualTo(500L);
        assertThat(first.reasoningTokens()).isEqualTo(120L);
        assertThat(first.latencyMs()).isEqualTo(300L);
        assertThat(first.cost()).isEqualByComparingTo("0.00300000");
        assertThat(first.model()).isEqualTo("test-model");
        assertThat(first.rounds()).isEqualTo(2);

        LlmUsageSummary second = view.turns().get(1).usage();
        assertThat(second.inputTokens()).isEqualTo(800L);
        assertThat(second.uncachedInputTokens()).isEqualTo(600L);
        assertThat(second.latencyMs()).isEqualTo(90L);
        assertThat(second.rounds()).isEqualTo(1);

        // 총계는 턴 합계다 — 턴을 다 더하면 정확히 총계가 나온다.
        LlmUsageTotals totals = view.usage();
        assertThat(totals.inputTokens()).isEqualTo(3_300L);
        assertThat(totals.uncachedInputTokens()).isEqualTo(2_100L);
        assertThat(totals.cachedInputTokens()).isEqualTo(1_200L);
        assertThat(totals.outputTokens()).isEqualTo(600L);
        assertThat(totals.reasoningTokens()).isEqualTo(150L);
        assertThat(totals.latencyMs()).isEqualTo(390L);
        assertThat(totals.cost()).isEqualByComparingTo("0.00390000");
        assertThat(totals.rounds()).isEqualTo(3);
    }

    @Test
    void 입력_토큰을_모르는_호출은_신규_입력_합계에서_빠진다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));
        attempt.applyTurn("첫 턴", generated("첫 내용", "첫 요약"));
        attemptRepository.save(attempt);

        llmCallRepository.saveAll(List.of(
                codeCall(attempt.id(), 0, usage(1, 1_000L, 200L, 400L, 50L, 120L), "0.00120000"),
                // 제공자가 사용량을 주지 않은 호출. 0이 아니라 "모름"이라 합계에서 빠진다.
                codeCall(attempt.id(), 0, usage(2, null, null, null, null, 80L), null)
        ));

        LlmUsageSummary usage = attemptQueryRepository.findById(attempt.id())
                .orElseThrow()
                .turns()
                .getFirst()
                .usage();

        assertThat(usage.inputTokens()).isEqualTo(1_000L);
        assertThat(usage.uncachedInputTokens()).isEqualTo(600L);
        // 호출은 실제로 있었으므로 횟수와 시간에는 잡힌다.
        assertThat(usage.rounds()).isEqualTo(2);
        assertThat(usage.latencyMs()).isEqualTo(200L);
    }

    @Test
    void 입력만_모르고_캐시는_아는_호출이_섞이면_신규와_캐시의_합이_입력과_다르다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));
        attempt.applyTurn("첫 턴", generated("첫 내용", "첫 요약"));
        attemptRepository.save(attempt);

        llmCallRepository.saveAll(List.of(
                codeCall(attempt.id(), 0, usage(1, 1_000L, 200L, 400L, 50L, 120L), "0.00120000"),
                codeCall(attempt.id(), 0, usage(2, null, 100L, 300L, 20L, 80L), null)
        ));

        LlmUsageSummary usage = attemptQueryRepository.findById(attempt.id())
                .orElseThrow()
                .turns()
                .getFirst()
                .usage();

        // 신규 입력은 행 단위로 빼서 더하므로 입력을 모르는 행은 통째로 빠진다. 반면 캐시 합계에는
        // 그 행의 300이 들어간다 — 그래서 600 + 700 != 1000이다. 클라이언트는 세 값을 각각 쓰고
        // 서로 빼거나 더해 만들지 않는다.
        assertThat(usage.inputTokens()).isEqualTo(1_000L);
        assertThat(usage.uncachedInputTokens()).isEqualTo(600L);
        assertThat(usage.cachedInputTokens()).isEqualTo(700L);
    }

    @Test
    void 총계는_턴에_속하지_않는_호출을_제외한다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));
        attempt.applyTurn("첫 턴", generated("첫 내용", "첫 요약"));
        attemptRepository.save(attempt);

        llmCallRepository.saveAll(List.of(
                codeCall(attempt.id(), 0, usage(1, 1_000L, 200L, 400L, 50L, 120L), "0.00120000"),
                // 제출 시 피드백 생성 호출. 서비스가 부담하는 비용이라 사용자 총계에서 뺀다.
                AttemptLlmCall.success(
                        attempt.id(),
                        null,
                        LlmCallPurpose.FEEDBACK,
                        usage(1, 2_000L, 400L, 0L, 100L, 500L),
                        new BigDecimal("0.00280000")
                ),
                // 턴이 저장되지 않은 실패 호출의 flush 행과 실패 마커.
                AttemptLlmCall.success(
                        attempt.id(),
                        null,
                        LlmCallPurpose.CODE,
                        usage(1, 900L, 0L, 0L, 0L, 700L),
                        new BigDecimal("0.00110000")
                ),
                AttemptLlmCall.failed(attempt.id(), LlmCallPurpose.CODE, 2, "provider-error")
        ));

        AttemptView view = attemptQueryRepository.findById(attempt.id()).orElseThrow();

        LlmUsageTotals totals = view.usage();
        assertThat(totals.inputTokens()).isEqualTo(1_000L);
        assertThat(totals.uncachedInputTokens()).isEqualTo(600L);
        assertThat(totals.cachedInputTokens()).isEqualTo(400L);
        assertThat(totals.outputTokens()).isEqualTo(200L);
        assertThat(totals.reasoningTokens()).isEqualTo(50L);
        assertThat(totals.latencyMs()).isEqualTo(120L);
        assertThat(totals.cost()).isEqualByComparingTo("0.00120000");
        assertThat(totals.rounds()).isEqualTo(1);
    }

    @Test
    void 턴에_속하지_않는_호출만_있으면_총계가_null이다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));

        llmCallRepository.saveAll(List.of(AttemptLlmCall.success(
                attempt.id(),
                null,
                LlmCallPurpose.FEEDBACK,
                usage(1, 2_000L, 400L, 0L, 100L, 500L),
                new BigDecimal("0.00280000")
        )));

        AttemptView view = attemptQueryRepository.findById(attempt.id()).orElseThrow();

        assertThat(view.usage()).isNull();
    }

    @Test
    void 사용량_행이_없는_턴은_usage가_null이다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));
        attempt.applyTurn("첫 턴", generated("첫 내용", "첫 요약"));
        attemptRepository.save(attempt);

        AttemptView view = attemptQueryRepository.findById(attempt.id()).orElseThrow();

        assertThat(view.turns().getFirst().usage()).isNull();
        assertThat(view.usage()).isNull();
    }

    private AttemptLlmCall codeCall(Long attemptId, int turnOrdinal, LlmCallUsage usage, String cost) {
        return AttemptLlmCall.success(
                attemptId, turnOrdinal, LlmCallPurpose.CODE, usage, cost == null ? null : new BigDecimal(cost));
    }

    private LlmCallUsage usage(int seq, Long input, Long output, Long cached, Long reasoning, long latencyMs) {
        return new LlmCallUsage(seq, "test-model", input, output, cached, reasoning, latencyMs);
    }

    private GeneratedCode generated(String content, String summary) {
        return new GeneratedCode(List.of(new ProblemFile("src/Main.java", content)), summary, List.of(), List.of());
    }

    private Problem newProblem() {
        return problemRepository.save(
                new Problem(null, "Hello World 출력", "# Hello World 출력", List.of(SKELETON), List.of()));
    }
}
