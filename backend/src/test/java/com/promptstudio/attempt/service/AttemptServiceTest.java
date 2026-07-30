package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.attempt.exception.AttemptAlreadySubmittedException;
import com.promptstudio.attempt.exception.AttemptHasNoTurnsException;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.exception.FeedbackGenerationInProgressException;
import com.promptstudio.attempt.exception.FeedbackNotFoundException;
import com.promptstudio.problem.exception.InactiveProblemException;
import com.promptstudio.attempt.port.FeedbackGenerationException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    void 비활성_문제로_시작하면_예외를_던진다() {
        Problem problem = newProblem();
        problem.deactivate();
        problemRepository.save(problem);

        assertThatThrownBy(() -> attemptService.startAttempt(problem.id()))
                .isInstanceOf(InactiveProblemException.class)
                .hasMessage("문제 ID " + problem.id() + "는 비활성 상태여서 새로 시작할 수 없습니다.");
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
        assertThat(updated.turns().getFirst().toolCalls())
                .containsExactly(new ToolCallEntry("edit_file", "src/main/java/Main.java"));
        assertThat(attemptService.getAttempt(started.id())).isEqualTo(updated);
    }

    @Test
    void 턴을_추가할_때_문제와_현재_어템프트와_새_프롬프트를_코드_생성기에_전달한다() {
        Problem problem = newProblem();
        AttemptView started = attemptService.startAttempt(problem.id());

        attemptService.addTurn(started.id(), "Hello 출력해줘");

        assertThat(codeGenerator.receivedProblem().id()).isEqualTo(problem.id());
        assertThat(codeGenerator.receivedProblem().specMd()).isEqualTo("명세");
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
    void 제출하면_피드백을_생성해_저장하고_반환한다() {
        Problem problem = newProblem();
        AttemptView started = attemptService.startAttempt(problem.id());
        AttemptView withTurn = attemptService.addTurn(started.id(), "Hello 출력해줘");

        AttemptView submitted = attemptService.submit(started.id());

        assertThat(submitted.feedback()).isEqualTo("생성된 피드백");
        assertThat(submitted.turns()).extracting(AttemptView.TurnView::feedback).containsExactly("턴 1 피드백");
        assertThat(feedbackGenerator.receivedProblem().id()).isEqualTo(problem.id());
        assertThat(feedbackGenerator.receivedAttempt()).isEqualTo(withTurn);
        assertThat(attemptService.getAttempt(started.id()).status()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(attemptService.getAttempt(started.id()).feedback()).isEqualTo("생성된 피드백");
    }

    @Test
    void 제출된_어템프트의_피드백을_조회하면_저장된_피드백을_반환한다() {
        AttemptView started = attemptService.startAttempt(newProblem().id());
        attemptService.addTurn(started.id(), "Hello 출력해줘");
        attemptService.submit(started.id());

        AttemptView feedback = attemptService.getFeedback(started.id());

        assertThat(feedback.feedback()).isEqualTo("생성된 피드백");
        assertThat(feedback.turns()).extracting(AttemptView.TurnView::feedback).containsExactly("턴 1 피드백");
    }

    @Test
    void 제출_전에_피드백을_조회하면_예외를_던진다() {
        AttemptView started = attemptService.startAttempt(newProblem().id());
        attemptService.addTurn(started.id(), "Hello 출력해줘");

        assertThatThrownBy(() -> attemptService.getFeedback(started.id()))
                .isInstanceOf(FeedbackNotFoundException.class)
                .hasMessage("어템프트 ID " + started.id() + "의 피드백이 아직 없습니다. 제출 후 조회할 수 있습니다.");
    }

    @Test
    void 없는_어템프트의_피드백을_조회하면_예외를_던진다() {
        assertThatThrownBy(() -> attemptService.getFeedback(999L))
                .isInstanceOf(AttemptNotFoundException.class)
                .hasMessage("어템프트 ID 999를 찾을 수 없습니다.");
    }

    @Test
    void 제출된_어템프트에_턴을_추가하면_AI를_호출하지_않고_예외를_던진다() {
        AttemptView started = attemptService.startAttempt(newProblem().id());
        attemptService.addTurn(started.id(), "Hello 출력해줘");
        attemptService.submit(started.id());
        codeGenerator.reset();

        assertThatThrownBy(() -> attemptService.addTurn(started.id(), "한 번 더 고쳐줘"))
                .isInstanceOf(AttemptAlreadySubmittedException.class)
                .hasMessage("어템프트 ID " + started.id() + "는 이미 제출되었습니다.");
        assertThat(codeGenerator.invocationCount()).isZero();
    }

    @Test
    void 이미_제출된_어템프트를_다시_제출하면_AI를_재호출하지_않고_저장된_피드백을_반환한다() {
        AttemptView started = attemptService.startAttempt(newProblem().id());
        attemptService.addTurn(started.id(), "Hello 출력해줘");
        feedbackGenerator.reset();

        AttemptView first = attemptService.submit(started.id());
        AttemptView second = attemptService.submit(started.id());

        assertThat(first.feedback()).isEqualTo("생성된 피드백");
        assertThat(second.feedback()).isEqualTo("생성된 피드백");
        assertThat(second.turns()).extracting(AttemptView.TurnView::feedback).containsExactly("턴 1 피드백");
        assertThat(feedbackGenerator.invocationCount()).isEqualTo(1);
    }

    @Test
    void 피드백_생성_중에는_제출과_턴_추가가_모두_거부된다() throws Exception {
        AttemptView started = attemptService.startAttempt(newProblem().id());
        attemptService.addTurn(started.id(), "Hello 출력해줘");
        codeGenerator.reset();
        feedbackGenerator.reset();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        feedbackGenerator.blockNextWith(entered, gate);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<AttemptView> inFlight = executor.submit(() -> attemptService.submit(started.id()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> attemptService.submit(started.id()))
                    .isInstanceOf(FeedbackGenerationInProgressException.class)
                    .hasMessage("어템프트 ID " + started.id() + "는 피드백 생성이 진행 중입니다.");
            assertThatThrownBy(() -> attemptService.addTurn(started.id(), "한 번 더 고쳐줘"))
                    .isInstanceOf(FeedbackGenerationInProgressException.class)
                    .hasMessage("어템프트 ID " + started.id() + "는 피드백 생성이 진행 중입니다.");
            assertThat(codeGenerator.invocationCount()).isZero();

            gate.countDown();

            assertThat(inFlight.get(5, TimeUnit.SECONDS).feedback()).isEqualTo("생성된 피드백");
            assertThat(attemptService.submit(started.id()).feedback()).isEqualTo("생성된 피드백");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 피드백_생성이_실패하면_상태가_유지되고_재시도할_수_있다() {
        AttemptView started = attemptService.startAttempt(newProblem().id());
        attemptService.addTurn(started.id(), "Hello 출력해줘");
        feedbackGenerator.failNextWith(new FeedbackGenerationException("AI 호출 실패"));

        assertThatThrownBy(() -> attemptService.submit(started.id()))
                .isInstanceOf(FeedbackGenerationException.class);
        assertThat(attemptService.getAttempt(started.id()).status()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(attemptService.getAttempt(started.id()).feedback()).isNull();
        assertThat(attemptService.submit(started.id()).feedback()).isEqualTo("생성된 피드백");
    }

    @Test
    void 턴이_없는_어템프트를_제출하면_예외를_던진다() {
        AttemptView started = attemptService.startAttempt(newProblem().id());

        assertThatThrownBy(() -> attemptService.submit(started.id()))
                .isInstanceOf(AttemptHasNoTurnsException.class)
                .hasMessage("어템프트 ID " + started.id() + "에 턴이 없어 피드백을 생성할 수 없습니다.");
    }

    @Test
    void 없는_어템프트를_제출하면_예외를_던진다() {
        assertThatThrownBy(() -> attemptService.submit(999L))
                .isInstanceOf(AttemptNotFoundException.class)
                .hasMessage("어템프트 ID 999를 찾을 수 없습니다.");
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem("hello-world", "제목", "명세", List.of(SKELETON)));
    }
}
