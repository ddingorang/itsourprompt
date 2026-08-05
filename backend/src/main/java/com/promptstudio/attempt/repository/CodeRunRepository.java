package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.CodeRunCase;
import com.promptstudio.attempt.domain.CodeRunCaseTally;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunSummary;
import com.promptstudio.attempt.domain.CodeRunView;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CodeRunRepository {

    /**
     * QUEUED 행을 삽입한다. 같은 어템프트에 이미 미완료 run이 있으면 부분 유니크 인덱스에 걸려 false를 반환한다.
     * 예외 대신 boolean으로 돌려주는 이유는 {@code GlobalExceptionHandler}가 DataIntegrityViolationException을
     * 이미 다른 의미로 매핑하고 있기 때문이다.
     */
    boolean tryInsertQueued(UUID runId, Long attemptId, Integer turnOrdinal, Instant now);

    Optional<CodeRunView> findByIdAndAttemptId(UUID runId, Long attemptId);

    /**
     * 실행 한 건의 채점 케이스를 리포트 순서대로 반환한다. 케이스 기록 이전에 실행된 run이면 빈 목록이다.
     */
    List<CodeRunCase> findCasesByRunId(UUID runId);

    /**
     * 여러 실행의 케이스 집계를 한 번에 읽는다. 목록 화면이 실행마다 단건 조회를 반복하지 않게 하려는 것이다.
     *
     * @return runId → 집계. 케이스가 없는 실행은 키가 없다
     */
    Map<UUID, CodeRunCaseTally> tallyCasesByRunIds(Collection<UUID> runIds);

    /**
     * 어템프트의 실행 기록을 최근 순으로 전부 반환한다. 실행이 없으면 빈 목록이다.
     *
     * <p>본문(stdout·stderr) 없는 요약만 담는다({@link CodeRunSummary} 참고).
     * 정렬은 부분 인덱스가 아닌 idx_code_run_attempt (attempt_id, created_at DESC)가 그대로 받는다.
     */
    List<CodeRunSummary> findAllByAttemptId(Long attemptId);

    /**
     * staleBefore보다 오래된 QUEUED를 RUNNER_ERROR로 전환한다. 워커가 죽어 좌초된 run이
     * 부분 유니크 인덱스 때문에 새 실행을 영구히 막는 것을 방지한다.
     *
     * @return 회수한 행 수
     */
    int expireStale(Instant staleBefore, Instant now);

    /**
     * QUEUED인 행에만 결과를 반영한다. 메시지 재전달이나 TTL 회수와 겹쳐 이미 종료된 run에
     * 뒤늦은 결과가 도착할 수 있으므로 조건부 UPDATE여야 한다.
     *
     * @return 반영한 행 수. 0이면 이미 종료된 run이라 무시해야 한다.
     */
    int applyResult(CodeRunResult result, Instant finishedAt);
}
