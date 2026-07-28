package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.exception.AttemptHasNoTurnsException;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(FakeAiConfiguration.class)
class AttemptServiceTest extends DatabaseTest {

    private static final ProblemFile SKELETON = new ProblemFile("src/main/java/Main.java", "class Main {}");

    @Autowired
    private AttemptService attemptService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private FakeAiConfiguration.FakeCodeGenerator codeGenerator;

    @Autowired
    private FakeAiConfiguration.FakeFeedbackGenerator feedbackGenerator;

    @Test
    void 어템프트를_시작하면_문제_스켈레톤으로_초기화해_저장한다() {
        Problem problem = newProblem();

        AttemptView started = attemptService.startAttempt(problem.id());

        assertThat(started.id()).isNotNull();
        assertThat(started.problemId()).isEqualTo(problem.id());
        assertThat(started.files()).containsExactly(SKELETON);
        assertThat(started.turns()).isEmpty();
        assertThat(attemptService.getAttempt(started.id())).isEqualTo(started);
    }

    @Test
    void 없는_문제로_시작하면_예외를_던진다() {
        assertThatThrownBy(() -> attemptService.startAttempt(999L))
                .isInstanceOf(ProblemNotFoundException.class)
                .hasMessage("문제 ID 999를 찾을 수 없습니다.");
    }

    @Test
    void 저장된_어템프트를_ID로_조회한다() {
        AttemptView started = attemptService.startAttempt(newProblem().id());

        assertThat(attemptService.getAttempt(started.id())).isEqualTo(started);
    }

    @Test
    void 없는_어템프트를_조회하면_예외를_던진다() {
        assertThatThrownBy(() -> attemptService.getAttempt(999L))
                .isInstanceOf(AttemptNotFoundException.class)
                .hasMessage("어템프트 ID 999를 찾을 수 없습니다.");
    }

    @Test
    void 턴을_추가하면_생성_결과로_어템프트를_갱신해_저장한다() {
        AttemptView started = attemptService.startAttempt(newProblem().id());

        AttemptView updated = attemptService.addTurn(started.id(), "Hello 출력해줘");

        assertThat(updated.id()).isEqualTo(started.id());
        assertThat(updated.files())
                .containsExactly(new ProblemFile("src/main/java/Main.java", "생성된 내용"));
        assertThat(updated.turns()).hasSize(1);
        assertThat(updated.turns().getFirst().userPrompt()).isEqualTo("Hello 출력해줘");
        assertThat(updated.turns().getFirst().aiSummary()).isEqualTo("생성 요약");
        assertThat(attemptService.getAttempt(started.id())).isEqualTo(updated);
    }

    @Test
    void 턴을_추가할_때_현재_어템프트와_새_프롬프트를_코드_생성기에_전달한다() {
        AttemptView started = attemptService.startAttempt(newProblem().id());

        attemptService.addTurn(started.id(), "Hello 출력해줘");

        assertThat(codeGenerator.receivedAttempt()).isEqualTo(started);
        assertThat(codeGenerator.receivedPrompt()).isEqualTo("Hello 출력해줘");
    }

    @Test
    void 없는_어템프트에_턴을_추가하면_예외를_던진다() {
        assertThatThrownBy(() -> attemptService.addTurn(999L, "Hello 출력해줘"))
                .isInstanceOf(AttemptNotFoundException.class)
                .hasMessage("어템프트 ID 999를 찾을 수 없습니다.");
    }

    @Test
    void 피드백_요청시_문제와_어템프트를_피드백_생성기에_전달한다() {
        Problem problem = newProblem();
        AttemptView started = attemptService.startAttempt(problem.id());
        AttemptView withTurn = attemptService.addTurn(started.id(), "Hello 출력해줘");

        String feedback = attemptService.generateFeedback(started.id());

        assertThat(feedback).isEqualTo("생성된 피드백");
        assertThat(feedbackGenerator.receivedProblem().id()).isEqualTo(problem.id());
        assertThat(feedbackGenerator.receivedAttempt()).isEqualTo(withTurn);
    }

    @Test
    void 턴이_없는_어템프트로_피드백을_요청하면_예외를_던진다() {
        AttemptView started = attemptService.startAttempt(newProblem().id());

        assertThatThrownBy(() -> attemptService.generateFeedback(started.id()))
                .isInstanceOf(AttemptHasNoTurnsException.class)
                .hasMessage("어템프트 ID " + started.id() + "에 턴이 없어 피드백을 생성할 수 없습니다.");
    }

    @Test
    void 없는_어템프트로_피드백을_요청하면_예외를_던진다() {
        assertThatThrownBy(() -> attemptService.generateFeedback(999L))
                .isInstanceOf(AttemptNotFoundException.class)
                .hasMessage("어템프트 ID 999를 찾을 수 없습니다.");
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem("제목", "명세", List.of(SKELETON)));
    }
}
