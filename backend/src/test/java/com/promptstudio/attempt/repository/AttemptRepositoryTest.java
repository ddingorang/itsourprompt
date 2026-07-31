package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 쓰기 seam. 저장은 JPA로, 확인은 조회 seam(jOOQ)으로 한다 — 운영 경로와 같은 조합이다.
 */
class AttemptRepositoryTest extends DatabaseTest {

    private static final ProblemFile SKELETON = new ProblemFile("src/Main.java", "class Main {}");

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private AttemptQueryRepository attemptQueryRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private DSLContext dsl;

    @Test
    void 저장한_어템프트를_ID로_조회한다() {
        Problem problem = newProblem();

        Attempt saved = attemptRepository.save(Attempt.start(problem, ownerId));

        AttemptView found = attemptQueryRepository.findById(saved.id()).orElseThrow();
        assertThat(found.problemId()).isEqualTo(problem.id());
        assertThat(found.files()).containsExactly(SKELETON);
        assertThat(found.turns()).isEmpty();
    }

    @Test
    void 저장하면_DB가_ID를_발급한다() {
        Problem problem = newProblem();

        Attempt first = attemptRepository.save(Attempt.start(problem, ownerId));
        Attempt second = attemptRepository.save(Attempt.start(problem, ownerId));

        assertThat(first.id()).isNotNull();
        assertThat(second.id()).isGreaterThan(first.id());
    }

    @Test
    void 턴을_추가해_다시_저장하면_턴이_보존된다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));

        attempt.applyTurn("Hello 출력해줘", generated("생성된 내용", "생성 요약"));
        attemptRepository.save(attempt);

        AttemptView found = attemptQueryRepository.findById(attempt.id()).orElseThrow();
        assertThat(found.turns()).hasSize(1);
        assertThat(found.turns().getFirst().userPrompt()).isEqualTo("Hello 출력해줘");
        assertThat(found.turns().getFirst().aiSummary()).isEqualTo("생성 요약");
    }

    @Test
    void 턴_순서와_변경파일_순서를_보존한다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));

        attempt.applyTurn("Main을 채워줘", generated("생성된 내용", "첫 요약"));
        attempt.applyTurn("Util도 만들어줘", new GeneratedCode(
                List.of(
                        new ProblemFile("src/Main.java", "생성된 내용"),
                        new ProblemFile("src/Util.java", "class Util {}")
                ),
                "둘째 요약",
                List.of(),
                List.of()
        ));
        attemptRepository.save(attempt);

        AttemptView found = attemptQueryRepository.findById(attempt.id()).orElseThrow();
        assertThat(found.turns()).hasSize(2);
        assertThat(found.turns().get(0).aiSummary()).isEqualTo("첫 요약");
        assertThat(found.turns().get(0).changes())
                .containsExactly(new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED, "생성된 내용"));
        assertThat(found.turns().get(1).aiSummary()).isEqualTo("둘째 요약");
        assertThat(found.turns().get(1).changes())
                .containsExactly(new FileChange("src/Util.java", FileChange.ChangeType.ADDED, "class Util {}"));
    }

    @Test
    void 턴의_툴콜_트레이스를_저장하고_순서대로_조회한다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));

        attempt.applyTurn("Main을 채워줘", new GeneratedCode(
                List.of(new ProblemFile("src/Main.java", "생성된 내용")),
                "요약",
                List.of(
                        new ToolCallEntry("list_files", null),
                        new ToolCallEntry("read_file", "src/Main.java"),
                        new ToolCallEntry("edit_file", "src/Main.java")
                ),
                List.of()
        ));
        attemptRepository.save(attempt);

        AttemptView found = attemptQueryRepository.findById(attempt.id()).orElseThrow();
        assertThat(found.turns().getFirst().toolCalls()).containsExactly(
                new ToolCallEntry("list_files", null),
                new ToolCallEntry("read_file", "src/Main.java"),
                new ToolCallEntry("edit_file", "src/Main.java")
        );
    }

    @Test
    void 제출한_어템프트의_턴별_피드백을_저장하고_순서대로_조회한다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));

        attempt.applyTurn("Main을 채워줘", generated("생성된 내용", "첫 요약"));
        attempt.applyTurn("Main을 다시 고쳐줘", generated("다시 생성된 내용", "둘째 요약"));
        attempt.submit(new AttemptFeedback(List.of("첫 턴 피드백", "둘째 턴 피드백"), "전체 피드백", List.of()));
        attemptRepository.save(attempt);

        AttemptView found = attemptQueryRepository.findById(attempt.id()).orElseThrow();
        assertThat(found.turns())
                .extracting(AttemptView.TurnView::feedback)
                .containsExactly("첫 턴 피드백", "둘째 턴 피드백");
        assertThat(found.feedback()).isEqualTo("전체 피드백");
    }

    @Test
    void 제출하기_전_턴은_피드백이_비어_있다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));

        attempt.applyTurn("Main을 채워줘", generated("생성된 내용", "첫 요약"));
        attemptRepository.save(attempt);

        AttemptView found = attemptQueryRepository.findById(attempt.id()).orElseThrow();
        assertThat(found.turns().getFirst().feedback()).isNull();
    }

    @Test
    void 다시_저장하면_현재_파일이_교체된다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));

        attempt.applyTurn("Util만 남겨줘", new GeneratedCode(
                List.of(new ProblemFile("src/Util.java", "class Util {}")),
                "요약",
                List.of(),
                List.of()
        ));
        attemptRepository.save(attempt);

        AttemptView found = attemptQueryRepository.findById(attempt.id()).orElseThrow();
        assertThat(found.files()).containsExactly(new ProblemFile("src/Util.java", "class Util {}"));
    }

    @Test
    void 없는_ID면_빈_Optional을_반환한다() {
        assertThat(attemptRepository.findById(999L)).isEmpty();
    }

    @Test
    void 같은_어템프트의_같은_ordinal에_턴을_두_번_저장하면_실패한다() {
        Attempt attempt = attemptRepository.save(Attempt.start(newProblem(), ownerId));

        insertTurn(attempt.id());

        assertThatThrownBy(() -> insertTurn(attempt.id())).isInstanceOf(DuplicateKeyException.class);
    }

    private void insertTurn(Long attemptId) {
        dsl.execute(
                "INSERT INTO attempt_turn (attempt_id, ordinal, user_prompt, ai_summary) VALUES (?, 0, ?, ?)",
                attemptId, "Hello 출력해줘", "생성 요약"
        );
    }

    private GeneratedCode generated(String content, String summary) {
        return new GeneratedCode(List.of(new ProblemFile("src/Main.java", content)), summary, List.of(), List.of());
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem("hello-world", "제목", "명세", List.of(SKELETON)));
    }
}
