package com.promptstudio.attempt.port;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.List;
import java.util.UUID;

/**
 * 빌드/실행 잡을 워커로 넘기는 포트.
 *
 * <p>파일 내용을 그대로 실어 보낸다. attemptId만 넘기면 안 된다 — attempt_file은 턴마다 전량 교체되는
 * 가변 테이블이라, 워커가 읽는 시점에 다음 턴이 덮어써서 다른 코드를 빌드할 수 있다.
 * 테스트도 같은 이유로 실어 보낸다. 문제 동기화가 언제든 내용을 갈아끼우므로, 요청 시점의 테스트로
 * 채점되려면 그 순간의 내용이 메시지에 박혀 있어야 한다. 워커에 DB·저장소 자격증명을 주지 않는
 * 결정(docker-compose.yml 참고)과도 이 방식만 양립한다.
 */
public interface CodeRunPublisher {

    void publish(UUID runId, Long attemptId, List<ProblemFile> files, List<ProblemFile> testFiles);
}
