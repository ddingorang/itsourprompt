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
    void 턴별_사용량_합계와_전체_총계를_파생한다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));
        attempt.applyTurn("첫 턴", generated("첫 내용", "첫 요약"));
        attempt.applyTurn("둘째 턴", generated("둘째 내용", "둘째 요약"));
        attemptRepository.save(attempt);

        llmCallRepository.saveAll(List.of(
                codeCall(attempt.id(), 0, usage(1, 1_000L, 200L, 400L, 50L), "0.00120000"),
                codeCall(attempt.id(), 0, usage(2, 1_500L, 300L, 600L, 70L), "0.00180000"),
                codeCall(attempt.id(), 1, usage(1, 800L, 100L, 200L, 30L), "0.00090000"),
                AttemptLlmCall.success(
                        attempt.id(),
                        null,
                        LlmCallPurpose.FEEDBACK,
                        usage(1, 2_000L, 400L, 0L, 100L),
                        new BigDecimal("0.00280000")
                )
        ));

        AttemptView view = attemptQueryRepository.findById(attempt.id()).orElseThrow();

        LlmUsageSummary first = view.turns().get(0).usage();
        assertThat(first.inputTokens()).isEqualTo(2_500L);
        assertThat(first.outputTokens()).isEqualTo(500L);
        assertThat(first.cachedInputTokens()).isEqualTo(1_000L);
        assertThat(first.reasoningTokens()).isEqualTo(120L);
        assertThat(first.cost()).isEqualByComparingTo("0.00300000");
        assertThat(first.model()).isEqualTo("test-model");
        assertThat(first.rounds()).isEqualTo(2);

        LlmUsageSummary second = view.turns().get(1).usage();
        assertThat(second.inputTokens()).isEqualTo(800L);
        assertThat(second.rounds()).isEqualTo(1);

        // 총계는 턴에 속하지 않은 피드백 호출까지 포함한다.
        LlmUsageTotals totals = view.usage();
        assertThat(totals.inputTokens()).isEqualTo(5_300L);
        assertThat(totals.outputTokens()).isEqualTo(1_000L);
        assertThat(totals.cachedInputTokens()).isEqualTo(1_200L);
        assertThat(totals.reasoningTokens()).isEqualTo(250L);
        assertThat(totals.cost()).isEqualByComparingTo("0.00670000");
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
        return AttemptLlmCall.success(attemptId, turnOrdinal, LlmCallPurpose.CODE, usage, new BigDecimal(cost));
    }

    private LlmCallUsage usage(int seq, Long input, Long output, Long cached, Long reasoning) {
        return new LlmCallUsage(seq, "test-model", input, output, cached, reasoning, 100L);
    }

    private GeneratedCode generated(String content, String summary) {
        return new GeneratedCode(List.of(new ProblemFile("src/Main.java", content)), summary, List.of(), List.of());
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem(null, "Hello World 출력", "# Hello World 출력", List.of(SKELETON)));
    }
}
