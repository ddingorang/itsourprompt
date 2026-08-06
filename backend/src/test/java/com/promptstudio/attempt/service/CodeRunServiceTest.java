package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.domain.CodeRunView;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.exception.CodeRunInProgressException;
import com.promptstudio.attempt.exception.CodeRunNotFoundException;
import com.promptstudio.attempt.exception.TurnNotFoundException;
import com.promptstudio.attempt.repository.CodeRunRepository;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import com.promptstudio.support.FakeCodeRunConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({FakeAiConfiguration.class, FakeCodeRunConfiguration.class})
class CodeRunServiceTest extends DatabaseTest {

    private static final ProblemFile TEST_FILE =
            new ProblemFile("src/test/java/MainTest.java", "class MainTest {}");

    @Autowired
    private CodeRunService codeRunService;

    @Autowired
    private AttemptService attemptService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private CodeRunRepository codeRunRepository;

    @Autowired
    private FakeCodeRunConfiguration.FakeCodeRunPublisher publisher;

    @Autowired
    private FakeAiConfiguration.FakeCodeGenerator codeGenerator;

    @BeforeEach
    void 발행_기록을_비운다() {
        publisher.reset();
        codeGenerator.reset();
    }

    @Test
    void 실행을_요청하면_QUEUED로_저장하고_현재_파일을_발행한다() {
        Long attemptId = newAttempt();

        CodeRunView run = codeRunService.requestRun(attemptId, ownerId);

        assertThat(run.status()).isEqualTo(CodeRunStatus.QUEUED);
        assertThat(run.attemptId()).isEqualTo(attemptId);
        assertThat(publisher.invocationCount()).isEqualTo(1);
        assertThat(publisher.receivedAttemptId()).isEqualTo(attemptId);
        assertThat(publisher.publishedRunIds()).containsExactly(run.id());
        assertThat(publisher.receivedFiles())
                .containsExactly(new ProblemFile("src/main/java/Main.java", "class Main {}"));
    }

    /**
     * 채점용 테스트는 어템프트가 아니라 문제에서 온다. 발행 시점의 내용이 메시지에 박혀야
     * 그 뒤 동기화가 문제를 갈아끼워도 요청한 코드가 요청한 기준으로 채점된다.
     */
    @Test
    void 문제의_테스트_파일을_함께_발행한다() {
        Long attemptId = newAttempt();

        codeRunService.requestRun(attemptId);

        assertThat(publisher.receivedTestFiles()).containsExactly(TEST_FILE);
    }

    @Test
    void 테스트가_없는_문제는_빈_목록으로_발행한다() {
        Long attemptId = newAttempt(List.of());

        codeRunService.requestRun(attemptId);

        assertThat(publisher.receivedTestFiles()).isEmpty();
    }

    @Test
    void 턴이_없으면_스켈레톤을_실행하고_turnOrdinal은_null이다() {
        Long attemptId = newAttempt();

        CodeRunView run = codeRunService.requestRun(attemptId);

        assertThat(run.turnOrdinal()).isNull();
        assertThat(publisher.receivedFiles())
                .containsExactly(new ProblemFile("src/main/java/Main.java", "class Main {}"));
    }

    @Test
    void 턴을_지정하지_않으면_마지막_턴을_실행하고_그_번호를_기록한다() {
        Long attemptId = newAttemptWithTurns("버전 1", "버전 2");

        CodeRunView run = codeRunService.requestRun(attemptId);

        assertThat(run.turnOrdinal()).isEqualTo(1);
        assertThat(publisher.receivedFiles())
                .containsExactly(new ProblemFile("src/main/java/Main.java", "버전 2"));
    }

    /**
     * 턴은 불변이라 과거 턴을 다시 실행하면 그때의 코드가 그대로 나가야 한다.
     * 이게 "몇 번째 프롬프트까지 통과했는지"를 확인할 수 있게 하는 핵심이다.
     */
    @Test
    void 지정한_턴_시점의_코드를_발행한다() {
        Long attemptId = newAttemptWithTurns("버전 1", "버전 2");

        CodeRunView run = codeRunService.requestRun(attemptId, 0);

        assertThat(run.turnOrdinal()).isZero();
        assertThat(publisher.receivedFiles())
                .containsExactly(new ProblemFile("src/main/java/Main.java", "버전 1"));
    }

    @Test
    void 없는_턴을_지정하면_실패하고_발행하지_않는다() {
        Long attemptId = newAttemptWithTurns("버전 1");

        assertThatThrownBy(() -> codeRunService.requestRun(attemptId, 1))
                .isInstanceOf(TurnNotFoundException.class);

        assertThat(publisher.invocationCount()).isZero();
    }

    @Test
    void 음수_턴을_지정하면_실패한다() {
        Long attemptId = newAttemptWithTurns("버전 1");

        assertThatThrownBy(() -> codeRunService.requestRun(attemptId, -1))
                .isInstanceOf(TurnNotFoundException.class);
    }

    @Test
    void turnOrdinal은_결과_조회에서도_보인다() {
        Long attemptId = newAttemptWithTurns("버전 1", "버전 2");
        UUID runId = codeRunService.requestRun(attemptId, 0).id();

        codeRunService.applyResult(new CodeRunResult(
                runId, CodeRunStatus.TEST_FAILED, 1, "1 tests failed", "", 900L));

        assertThat(codeRunService.readRun(attemptId, AttemptOwner.user(ownerId), runId).turnOrdinal()).isZero();
    }

    @Test
    void 없는_어템프트로_요청하면_실패하고_발행하지_않는다() {
        assertThatThrownBy(() -> codeRunService.requestRun(999L, ownerId))
                .isInstanceOf(AttemptNotFoundException.class);

        assertThat(publisher.invocationCount()).isZero();
    }

    @Test
    void 미완료_실행이_있으면_두_번째_요청은_거부된다() {
        Long attemptId = newAttempt();
        codeRunService.requestRun(attemptId, ownerId);

        assertThatThrownBy(() -> codeRunService.requestRun(attemptId, ownerId))
                .isInstanceOf(CodeRunInProgressException.class);

        assertThat(publisher.invocationCount()).isEqualTo(1);
    }

    @Test
    void 이전_실행이_끝났으면_다시_요청할_수_있다() {
        Long attemptId = newAttempt();
        CodeRunView first = codeRunService.requestRun(attemptId, ownerId);
        codeRunService.applyResult(succeeded(first.id()));

        CodeRunView second = codeRunService.requestRun(attemptId, ownerId);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(publisher.invocationCount()).isEqualTo(2);
    }

    @Test
    void 결과가_도착하면_조회에_반영된다() {
        Long attemptId = newAttempt();
        CodeRunView queued = codeRunService.requestRun(attemptId, ownerId);

        codeRunService.applyResult(succeeded(queued.id()));

        CodeRunView run = codeRunService.readRun(attemptId, AttemptOwner.user(ownerId), queued.id());
        assertThat(run.status()).isEqualTo(CodeRunStatus.SUCCEEDED);
        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).isEqualTo("Hello World\n");
        assertThat(run.durationMs()).isEqualTo(1840L);
    }

    @Test
    void 종료된_실행에_도착한_결과는_무시된다() {
        Long attemptId = newAttempt();
        CodeRunView queued = codeRunService.requestRun(attemptId, ownerId);
        codeRunService.applyResult(succeeded(queued.id()));

        // 메시지 재전달을 가정한 두 번째 결과. 먼저 확정된 상태를 덮어쓰지 않아야 한다.
        codeRunService.applyResult(new CodeRunResult(
                queued.id(), CodeRunStatus.RUNTIME_ERROR, 1, "", "펑", 99L));

        CodeRunView run = codeRunService.readRun(attemptId, AttemptOwner.user(ownerId), queued.id());
        assertThat(run.status()).isEqualTo(CodeRunStatus.SUCCEEDED);
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void 모르는_실행_ID의_결과는_조용히_버려진다() {
        Long attemptId = newAttempt();

        codeRunService.applyResult(succeeded(UUID.randomUUID()));

        assertThatThrownBy(() -> codeRunService.readRun(attemptId, AttemptOwner.user(ownerId), UUID.randomUUID()))
                .isInstanceOf(CodeRunNotFoundException.class);
    }

    @Test
    void 다른_어템프트의_실행_ID로는_조회할_수_없다() {
        Long attemptId = newAttempt();
        Long otherAttemptId = newAttempt();
        CodeRunView run = codeRunService.requestRun(attemptId, ownerId);

        assertThatThrownBy(() -> codeRunService.readRun(otherAttemptId, AttemptOwner.user(ownerId), run.id()))
                .isInstanceOf(CodeRunNotFoundException.class);
    }

    @Test
    void TTL을_넘긴_QUEUED는_RUNNER_ERROR로_회수되고_재요청이_열린다() {
        Long attemptId = newAttempt();
        CodeRunView stranded = codeRunService.requestRun(attemptId, ownerId);

        // 워커가 죽어 좌초된 상황: 생성 시각을 TTL 밖으로 밀어낸다.
        Instant now = Instant.now();
        int expired = codeRunRepository.expireStale(now.plus(1, ChronoUnit.HOURS), now);
        assertThat(expired).isEqualTo(1);

        CodeRunView recovered = codeRunService.readRun(attemptId, AttemptOwner.user(ownerId), stranded.id());
        assertThat(recovered.status()).isEqualTo(CodeRunStatus.RUNNER_ERROR);
        assertThat(recovered.stderr()).isNotBlank();

        // 부분 유니크 인덱스가 풀려 새 실행을 요청할 수 있다.
        assertThat(codeRunService.requestRun(attemptId, ownerId).status()).isEqualTo(CodeRunStatus.QUEUED);
    }

    private CodeRunResult succeeded(UUID runId) {
        return new CodeRunResult(runId, CodeRunStatus.SUCCEEDED, 0, "Hello World\n", "", 1840L);
    }

    private Long newAttempt() {
        return newAttempt(List.of(TEST_FILE));
    }

    /**
     * 턴마다 다른 코드가 쌓인 어템프트를 만든다. 인자 순서가 턴 순서다.
     */
    private Long newAttemptWithTurns(String... contentsPerTurn) {
        Long attemptId = newAttempt();

        for (int index = 0; index < contentsPerTurn.length; index++) {
            codeGenerator.respondWith(FakeAiConfiguration.FakeCodeGenerator.generating(contentsPerTurn[index]));
            attemptService.addTurn(attemptId, ownerId, index + "번째 프롬프트");
        }

        return attemptId;
    }

    private Long newAttempt(List<ProblemFile> testFiles) {
        Problem problem = problemRepository.save(new Problem(
                "hello-world-" + UUID.randomUUID(),
                "Hello World 출력",
                "# 명세",
                List.of(new ProblemFile("src/main/java/Main.java", "class Main {}")),
                testFiles
        ));

        return attemptService.startAttempt(problem.id(), ownerId, null).id();
    }
}
