package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.exception.AttemptHasNoTurnsException;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.port.CodeGenerator;
import com.promptstudio.attempt.port.FeedbackGenerator;
import com.promptstudio.attempt.repository.AttemptRepository;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptServiceTest {

    private final Problem problem = new Problem(1L, "제목", "명세", List.of(
            new ProblemFile("src/Main.java", "class Main {}")
    ));
    private final FakeProblemRepository problemRepository = new FakeProblemRepository(List.of(problem));
    private final FakeAttemptRepository attemptRepository = new FakeAttemptRepository();
    private final FakeCodeGenerator codeGenerator = new FakeCodeGenerator();
    private final FakeFeedbackGenerator feedbackGenerator = new FakeFeedbackGenerator();
    private final AttemptService attemptService = new AttemptService(
            problemRepository,
            attemptRepository,
            codeGenerator,
            feedbackGenerator
    );

    @Test
    void 어템프트를_시작하면_문제_스켈레톤으로_초기화해_저장한다() {
        Attempt started = attemptService.startAttempt(1L);

        assertThat(started.id()).isNotNull();
        assertThat(started.problemId()).isEqualTo(1L);
        assertThat(started.currentFiles()).containsExactly(new ProblemFile("src/Main.java", "class Main {}"));
        assertThat(started.turns()).isEmpty();
        assertThat(attemptRepository.findById(started.id())).contains(started);
    }

    @Test
    void 없는_문제로_시작하면_예외를_던진다() {
        assertThatThrownBy(() -> attemptService.startAttempt(999L))
                .isInstanceOf(ProblemNotFoundException.class)
                .hasMessage("문제 ID 999를 찾을 수 없습니다.");
    }

    @Test
    void 저장된_어템프트를_ID로_조회한다() {
        Attempt started = attemptService.startAttempt(1L);

        Attempt found = attemptService.getAttempt(started.id());

        assertThat(found).isEqualTo(started);
    }

    @Test
    void 없는_어템프트를_조회하면_예외를_던진다() {
        assertThatThrownBy(() -> attemptService.getAttempt(999L))
                .isInstanceOf(AttemptNotFoundException.class)
                .hasMessage("어템프트 ID 999를 찾을 수 없습니다.");
    }

    @Test
    void 턴을_추가하면_생성_결과로_어템프트를_갱신해_저장한다() {
        Attempt started = attemptService.startAttempt(1L);

        Attempt updated = attemptService.addTurn(started.id(), "Hello 출력해줘");

        assertThat(updated.id()).isEqualTo(started.id());
        assertThat(updated.currentFiles()).containsExactly(new ProblemFile("src/Main.java", "생성된 내용"));
        assertThat(updated.turns()).hasSize(1);
        assertThat(updated.turns().getFirst().userPrompt()).isEqualTo("Hello 출력해줘");
        assertThat(updated.turns().getFirst().aiSummary()).isEqualTo("생성 요약");
        assertThat(attemptRepository.findById(started.id())).contains(updated);
    }

    @Test
    void 턴을_추가할_때_현재_어템프트와_새_프롬프트를_코드_생성기에_전달한다() {
        Attempt started = attemptService.startAttempt(1L);

        attemptService.addTurn(started.id(), "Hello 출력해줘");

        assertThat(codeGenerator.receivedAttempt).isEqualTo(started);
        assertThat(codeGenerator.receivedPrompt).isEqualTo("Hello 출력해줘");
    }

    @Test
    void 없는_어템프트에_턴을_추가하면_예외를_던진다() {
        assertThatThrownBy(() -> attemptService.addTurn(999L, "Hello 출력해줘"))
                .isInstanceOf(AttemptNotFoundException.class)
                .hasMessage("어템프트 ID 999를 찾을 수 없습니다.");
    }

    @Test
    void 피드백_요청시_문제와_어템프트를_피드백_생성기에_전달한다() {
        Attempt started = attemptService.startAttempt(1L);
        Attempt withTurn = attemptService.addTurn(started.id(), "Hello 출력해줘");

        String feedback = attemptService.generateFeedback(started.id());

        assertThat(feedback).isEqualTo("생성된 피드백");
        assertThat(feedbackGenerator.receivedProblem).isEqualTo(problem);
        assertThat(feedbackGenerator.receivedAttempt).isEqualTo(withTurn);
    }

    @Test
    void 턴이_없는_어템프트로_피드백을_요청하면_예외를_던진다() {
        Attempt started = attemptService.startAttempt(1L);

        assertThatThrownBy(() -> attemptService.generateFeedback(started.id()))
                .isInstanceOf(AttemptHasNoTurnsException.class)
                .hasMessage("어템프트 ID 1에 턴이 없어 피드백을 생성할 수 없습니다.");
    }

    @Test
    void 없는_어템프트로_피드백을_요청하면_예외를_던진다() {
        assertThatThrownBy(() -> attemptService.generateFeedback(999L))
                .isInstanceOf(AttemptNotFoundException.class)
                .hasMessage("어템프트 ID 999를 찾을 수 없습니다.");
    }

    private static class FakeCodeGenerator implements CodeGenerator {

        private final GeneratedCode result = new GeneratedCode(
                List.of(new ProblemFile("src/Main.java", "생성된 내용")),
                "생성 요약"
        );
        private Attempt receivedAttempt;
        private String receivedPrompt;

        @Override
        public GeneratedCode generate(Attempt attempt, String userPrompt) {
            this.receivedAttempt = attempt;
            this.receivedPrompt = userPrompt;

            return result;
        }
    }

    private static class FakeFeedbackGenerator implements FeedbackGenerator {

        private Problem receivedProblem;
        private Attempt receivedAttempt;

        @Override
        public String generate(Problem problem, Attempt attempt) {
            this.receivedProblem = problem;
            this.receivedAttempt = attempt;

            return "생성된 피드백";
        }
    }

    private static class FakeProblemRepository implements ProblemRepository {

        private final List<Problem> problems;

        private FakeProblemRepository(List<Problem> problems) {
            this.problems = problems;
        }

        @Override
        public List<Problem> findAll() {
            return problems;
        }

        @Override
        public Optional<Problem> findById(Long id) {
            for (Problem problem : problems) {
                if (problem.id().equals(id)) {
                    return Optional.of(problem);
                }
            }

            return Optional.empty();
        }
    }

    private static class FakeAttemptRepository implements AttemptRepository {

        private final Map<Long, Attempt> attempts = new HashMap<>();
        private long sequence = 0L;

        @Override
        public Attempt save(Attempt attempt) {
            Attempt saved = attempt.id() == null
                    ? new Attempt(++sequence, attempt.problemId(), attempt.currentFiles(), attempt.turns())
                    : attempt;
            attempts.put(saved.id(), saved);

            return saved;
        }

        @Override
        public Optional<Attempt> findById(Long id) {
            return Optional.ofNullable(attempts.get(id));
        }
    }
}
