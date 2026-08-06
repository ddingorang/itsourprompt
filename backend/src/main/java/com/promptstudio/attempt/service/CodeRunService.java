package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.CodeRunCaseTally;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.domain.CodeRunSummary;
import com.promptstudio.attempt.domain.CodeRunView;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.exception.CodeRunInProgressException;
import com.promptstudio.attempt.exception.CodeRunNotFoundException;
import com.promptstudio.attempt.exception.TurnNotFoundException;
import com.promptstudio.attempt.port.CodeRunPublisher;
import com.promptstudio.attempt.repository.AttemptQueryRepository;
import com.promptstudio.attempt.repository.CodeRunRepository;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final ApplicationEventPublisher eventPublisher;

    public CodeRunService(
            AttemptQueryRepository attemptQueryRepository,
            CodeRunRepository codeRunRepository,
            ProblemRepository problemRepository,
            CodeRunPublisher codeRunPublisher,
            ApplicationEventPublisher eventPublisher
    ) {
        this.attemptQueryRepository = attemptQueryRepository;
        this.codeRunRepository = codeRunRepository;
        this.problemRepository = problemRepository;
        this.codeRunPublisher = codeRunPublisher;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 마지막 턴의 코드를 실행한다. 턴이 없으면 시작 스켈레톤을 실행한다.
     */
    public CodeRunView requestRun(Long attemptId) {
        AttemptView attempt = getAttempt(attemptId);

        return run(attempt, attempt.headTurnOrdinal());
    }

    public CodeRunView requestRun(Long attemptId, Long userId) {
        return requestRun(attemptId, AttemptOwner.user(userId));
    }

    public CodeRunView requestRun(Long attemptId, AttemptOwner owner) {
        expireStaleRuns();

        AttemptView attempt = findAttempt(attemptId, owner)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));

        return run(attempt, attempt.headTurnOrdinal());
    }

    /**
     * 지정한 턴의 코드를 실행한다. 턴은 불변이라 과거 턴을 다시 돌려도 그때의 코드가 실행된다 —
     * "몇 번째 프롬프트까지 통과했는지"를 확인하는 용도다.
     *
     * <p>서비스 내부(릴레이 채점) 전용이다. 웹 경로는 소유자 검사가 있는 오버로드를 쓴다.
     */
    public CodeRunView requestRun(Long attemptId, int turnOrdinal) {
        AttemptView attempt = getAttempt(attemptId);

        if (turnOrdinal < 0 || turnOrdinal >= attempt.turns().size()) {
            throw new TurnNotFoundException(attemptId, turnOrdinal, attempt.turns().size());
        }

        return run(attempt, turnOrdinal);
    }

    /**
     * 소유자만 지정 턴을 실행할 수 있다.
     *
     * <p>랭킹이 남의 어템프트 ID를 공개하므로 검사가 없으면 표에서 긁은 ID로 남의 빌드/실행 컨테이너를
     * 띄울 수 있고, {@code uq_code_run_active}(어템프트당 미완료 실행 1건)로 상대의 실행을 409로
     * 막을 수도 있다 — 다른 쓰기 경로와 같은 관문을 지나게 한다.
     */
    public CodeRunView requestRun(Long attemptId, AttemptOwner owner, int turnOrdinal) {
        expireStaleRuns();

        AttemptView attempt = findAttempt(attemptId, owner)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));

        if (turnOrdinal < 0 || turnOrdinal >= attempt.turns().size()) {
            throw new TurnNotFoundException(attemptId, turnOrdinal, attempt.turns().size());
        }

        return run(attempt, turnOrdinal);
    }

    /**
     * 실행 기록에 turnOrdinal을 박아 두는 것이 핵심이다. 이게 없으면 결과가 도착하는 사이에 다음 턴이
     * 쌓였을 때 그 결과가 어느 코드에 대한 것인지 알 수 없다 — 턴 추가를 락으로 막는 대신
     * 결과를 올바른 턴에 귀속시켜 해결한다.
     */
    private CodeRunView run(AttemptView attempt, Integer turnOrdinal) {
        Long attemptId = attempt.id();

        // 문제가 아니라 어템프트에서 problemId를 얻는다. 실행 시점의 테스트로 채점된다.
        List<ProblemFile> testFiles = problemRepository.findTestFiles(attempt.problemId());
        String language = problemRepository.findLanguageById(attempt.problemId()).orElse("java");
        List<ProblemFile> files = attempt.filesAsOf(turnOrdinal);
        UUID runId = UUID.randomUUID();

        // 부분 유니크 인덱스(uq_code_run_active)가 어템프트당 미완료 run을 하나로 강제한다.
        if (!codeRunRepository.tryInsertQueued(runId, attemptId, turnOrdinal, Instant.now())) {
            throw new CodeRunInProgressException(attemptId);
        }

        // QUEUED 행이 커밋된 뒤에 발행한다.
        codeRunPublisher.publish(runId, attemptId, language, files, testFiles);
        log.info("[CODE RUN] queued | runId={} | attemptId={} | turnOrdinal={} | language={} | files={} | testFiles={}",
                runId, attemptId, turnOrdinal, language, files.size(), testFiles.size());

        return CodeRunView.queued(runId, attemptId, turnOrdinal);
    }

    private AttemptView getAttempt(Long attemptId) {
        expireStaleRuns();

        return attemptQueryRepository.findById(attemptId)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    /**
     * 케이스 집계를 한 번의 쿼리로 붙인다. 실행마다 조회하면 목록 길이에 비례해 쿼리가 늘어난다.
     * 케이스 기록이 없는 실행은 집계가 null로 남는다.
     */
    private List<CodeRunSummary> withTallies(List<CodeRunSummary> runs) {
        if (runs.isEmpty()) {
            return runs;
        }

        List<UUID> runIds = new ArrayList<>();

        for (CodeRunSummary run : runs) {
            runIds.add(run.id());
        }

        Map<UUID, CodeRunCaseTally> tallies = codeRunRepository.tallyCasesByRunIds(runIds);
        List<CodeRunSummary> withTallies = new ArrayList<>();

        for (CodeRunSummary run : runs) {
            withTallies.add(run.withTally(tallies.get(run.id())));
        }

        return withTallies;
    }

    /**
     * 어템프트의 실행 기록 전체를 최근 순으로 반환한다. 제출 완료(SUBMITTED)면 누구나, 아니면 소유자만.
     *
     * <p>실행 요청은 202로 접수증(runId)만 주고 결과는 뒤늦게 도착한다. 그 runId가 클라이언트에만
     * 있으면 새로고침 한 번에 사라지고, 그러면 진행 중인 실행을 조회할 수도 없고 다시 요청할 수도 없다
     * — 어템프트당 미완료 1건 제약 때문에 409가 나고 TTL 회수까지 기다려야 한다.
     * 그 상태를 서버에 물어볼 수 있게 하는 것이 이 조회의 목적이다.
     *
     * <p>실행 요청과 같은 소유자 조회를 쓰지 않고 갈라 둔 이유는 {@code AttemptService.readAttempt}와 같다:
     * 쓰기 경로가 소유권을 보장하는 자리에 "제출 상태 검사"를 얹으면 공개 범위를 넓히는 변경이
     * 조용히 쓰기까지 넓힌다. 조회는 어느 턴까지 통과했는지를 보여 주므로 공개 대상이다.
     */
    public List<CodeRunSummary> readRuns(Long attemptId, AttemptOwner requester) {
        // 조회 전에 먼저 회수한다. 빠뜨리면 좌초된 QUEUED가 "실행 중"으로 보이고, 그 상태로 새 실행을
        // 시도한 사용자는 409만 받는다 — 화면과 동작이 어긋난다.
        expireStaleRuns();

        findReadableAttempt(attemptId, requester)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));

        List<CodeRunSummary> runs = codeRunRepository.findAllByAttemptId(attemptId);

        return withTallies(runs);
    }

    /**
     * 실행 하나의 상태와 결과. 공개 규칙은 {@link #readRuns}와 같다.
     *
     * <p>결과는 폴링으로 받으므로 여기서도 먼저 회수한다 — 워커가 죽어 좌초된 QUEUED를 영원히
     * "실행 중"으로 보여 주면 사용자는 끝나지 않는 실행을 기다리게 된다.
     */
    public CodeRunView readRun(Long attemptId, AttemptOwner requester, UUID runId) {
        expireStaleRuns();

        findReadableAttempt(attemptId, requester)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));

        CodeRunView run = codeRunRepository.findByIdAndAttemptId(runId, attemptId)
                .orElseThrow(() -> new CodeRunNotFoundException(attemptId, runId));

        // 케이스는 별도 테이블이라 따로 읽는다. 진행 중(QUEUED)이면 아직 없으니 조회하지 않는다.
        return run.status() == CodeRunStatus.QUEUED
                ? run
                : run.withCases(codeRunRepository.findCasesByRunId(runId));
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

        // 반영된 결과만 알린다(applied == 0이면 위에서 반환). 무시된 재전달까지 알리면
        // 구독자(릴레이 채점)가 같은 턴을 두 번 전진시킨다.
        eventPublisher.publishEvent(new CodeRunFinishedEvent(result));
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

    /**
     * 읽기 경로가 보는 어템프트 — 내 것이거나, 제출 완료라 누구에게나 열린 것.
     */
    private java.util.Optional<AttemptView> findReadableAttempt(Long attemptId, AttemptOwner requester) {
        return findAttempt(attemptId, requester)
                .or(() -> attemptQueryRepository.findById(attemptId)
                        .filter(attempt -> attempt.status() == AttemptStatus.SUBMITTED));
    }
}
