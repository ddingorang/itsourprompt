package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.domain.CodeRunView;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.exception.CodeRunInProgressException;
import com.promptstudio.attempt.exception.CodeRunNotFoundException;
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

    @BeforeEach
    void 발행_기록을_비운다() {
        publisher.reset();
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

        CodeRunView run = codeRunService.getRun(attemptId, ownerId, queued.id());
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

        CodeRunView run = codeRunService.getRun(attemptId, ownerId, queued.id());
        assertThat(run.status()).isEqualTo(CodeRunStatus.SUCCEEDED);
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void 모르는_실행_ID의_결과는_조용히_버려진다() {
        Long attemptId = newAttempt();

        codeRunService.applyResult(succeeded(UUID.randomUUID()));

        assertThatThrownBy(() -> codeRunService.getRun(attemptId, ownerId, UUID.randomUUID()))
                .isInstanceOf(CodeRunNotFoundException.class);
    }

    @Test
    void 다른_어템프트의_실행_ID로는_조회할_수_없다() {
        Long attemptId = newAttempt();
        Long otherAttemptId = newAttempt();
        CodeRunView run = codeRunService.requestRun(attemptId, ownerId);

        assertThatThrownBy(() -> codeRunService.getRun(otherAttemptId, ownerId, run.id()))
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

        CodeRunView recovered = codeRunService.getRun(attemptId, ownerId, stranded.id());
        assertThat(recovered.status()).isEqualTo(CodeRunStatus.RUNNER_ERROR);
        assertThat(recovered.stderr()).isNotBlank();

        // 부분 유니크 인덱스가 풀려 새 실행을 요청할 수 있다.
        assertThat(codeRunService.requestRun(attemptId, ownerId).status()).isEqualTo(CodeRunStatus.QUEUED);
    }

    private CodeRunResult succeeded(UUID runId) {
        return new CodeRunResult(runId, CodeRunStatus.SUCCEEDED, 0, "Hello World\n", "", 1840L);
    }

    private Long newAttempt() {
        Problem problem = problemRepository.save(new Problem(
                "hello-world-" + UUID.randomUUID(),
                "Hello World 출력",
                "# 명세",
                List.of(new ProblemFile("src/main/java/Main.java", "class Main {}"))
        ));

        return attemptService.startAttempt(problem.id(), ownerId, null).id();
    }
}
