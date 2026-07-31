package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunView;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.exception.CodeRunInProgressException;
import com.promptstudio.attempt.exception.CodeRunNotFoundException;
import com.promptstudio.attempt.port.CodeRunPublisher;
import com.promptstudio.attempt.repository.AttemptQueryRepository;
import com.promptstudio.attempt.repository.CodeRunRepository;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 어템프트의 현재 파일을 워커로 보내 빌드/실행하고, 돌아온 결과를 반영한다.
 *
 * <p>이 클래스는 트랜잭션 경계를 열지 않는다 — QUEUED 행이 커밋된 뒤에 메시지를 발행해야 하기 때문이다.
 * 트랜잭션 안에서 발행하면 롤백 시 유령 메시지가 남고, 커밋 전에 발행하면 워커가 먼저 끝나
 * 아직 보이지 않는 행을 UPDATE 하려 한다.
 */
@Service
public class CodeRunService {

    /**
     * 이 시간을 넘긴 QUEUED는 워커가 죽은 것으로 본다 — 컴파일 30초 + 실행 30초(테스트) + 큐 대기 여유.
     */
    private static final Duration STALE_RUN_TTL = Duration.ofMinutes(2);

    private static final Logger log = LoggerFactory.getLogger(CodeRunService.class);

    private final AttemptQueryRepository attemptQueryRepository;
    private final CodeRunRepository codeRunRepository;
    private final ProblemRepository problemRepository;
    private final CodeRunPublisher codeRunPublisher;

    public CodeRunService(
            AttemptQueryRepository attemptQueryRepository,
            CodeRunRepository codeRunRepository,
            ProblemRepository problemRepository,
            CodeRunPublisher codeRunPublisher
    ) {
        this.attemptQueryRepository = attemptQueryRepository;
        this.codeRunRepository = codeRunRepository;
        this.problemRepository = problemRepository;
        this.codeRunPublisher = codeRunPublisher;
    }

    public CodeRunView requestRun(Long attemptId, Long userId) {
        return requestRun(attemptId, AttemptOwner.user(userId));
    }

    public CodeRunView requestRun(Long attemptId, AttemptOwner owner) {
        expireStaleRuns();

        AttemptView attempt = findAttempt(attemptId, owner)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));

        // 문제가 아니라 어템프트에서 problemId를 얻는다. 실행 시점의 테스트로 채점된다.
        List<ProblemFile> testFiles = problemRepository.findTestFiles(attempt.problemId());
        UUID runId = UUID.randomUUID();

        // 부분 유니크 인덱스(uq_code_run_active)가 어템프트당 미완료 run을 하나로 강제한다.
        if (!codeRunRepository.tryInsertQueued(runId, attemptId, Instant.now())) {
            throw new CodeRunInProgressException(attemptId);
        }

        // QUEUED 행이 커밋된 뒤에 발행한다.
        codeRunPublisher.publish(runId, attemptId, attempt.files(), testFiles);
        log.info("[CODE RUN] queued | runId={} | attemptId={} | files={} | testFiles={}",
                runId, attemptId, attempt.files().size(), testFiles.size());

        return CodeRunView.queued(runId, attemptId);
    }

    public CodeRunView getRun(Long attemptId, Long userId, UUID runId) {
        return getRun(attemptId, AttemptOwner.user(userId), runId);
    }

    public CodeRunView getRun(Long attemptId, AttemptOwner owner, UUID runId) {
        expireStaleRuns();

        findAttempt(attemptId, owner)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));

        return codeRunRepository.findByIdAndAttemptId(runId, attemptId)
                .orElseThrow(() -> new CodeRunNotFoundException(attemptId, runId));
    }

    /**
     * 결과 반영은 멱등하다. 메시지 재전달이나 TTL 회수와 겹쳐 이미 종료된 run에 결과가 도착하면 무시한다.
     */
    public void applyResult(CodeRunResult result) {
        int applied = codeRunRepository.applyResult(result, Instant.now());

        if (applied == 0) {
            log.info("[CODE RUN] 이미 종료된 실행의 결과를 무시합니다 | runId={} | status={}",
                    result.runId(), result.status());

            return;
        }

        log.info("[CODE RUN] finished | runId={} | status={} | exitCode={} | duration={} ms",
                result.runId(), result.status(), result.exitCode(), result.durationMs());
    }

    private void expireStaleRuns() {
        Instant now = Instant.now();
        int expired = codeRunRepository.expireStale(now.minus(STALE_RUN_TTL), now);

        if (expired > 0) {
            log.warn("[CODE RUN] 좌초된 실행 {}건을 RUNNER_ERROR로 회수했습니다.", expired);
        }
    }

    private java.util.Optional<AttemptView> findAttempt(Long attemptId, AttemptOwner owner) {
        if (owner.isUser()) {
            return attemptQueryRepository.findByIdAndUserId(attemptId, owner.userId());
        }
        return attemptQueryRepository.findByIdAndGuestSessionId(attemptId, owner.guestSessionId());
    }
}
