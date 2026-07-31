package com.promptstudio.attempt.port;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.List;
import java.util.UUID;

/**
 * 빌드/실행 잡을 워커로 넘기는 포트.
 *
 * <p>파일 내용을 그대로 실어 보낸다. attemptId만 넘기면 안 된다 — attempt_file은 턴마다 전량 교체되는
 * 가변 테이블이라, 워커가 읽는 시점에 다음 턴이 덮어써서 다른 코드를 빌드할 수 있다.
 */
public interface CodeRunPublisher {

    void publish(UUID runId, Long attemptId, List<ProblemFile> files);
}
