package com.promptstudio.attempt.domain;

import java.util.List;
import java.util.UUID;

/**
 * 워커가 돌려준 실행 결과. 전송 계층(rabbit)의 메시지를 도메인으로 번역한 형태다.
 *
 * @param cases 채점 테스트의 케이스별 결과. 테스트 없이 main만 실행했거나, 컴파일 실패·타임아웃으로
 *              리포트가 생기지 않았거나, 케이스 기록 이전 버전의 워커가 보낸 결과면 빈 목록이다.
 *              {@code status}는 워커의 종료 코드 판정이고 이 목록이 그것을 뒤집지 않는다.
 */
public record CodeRunResult(
        UUID runId,
        CodeRunStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        Long durationMs,
        List<CodeRunCase> cases
) {

    /** 케이스가 없는 결과. 기존 호출부(테스트 다수)를 그대로 두기 위한 생성자다. */
    public CodeRunResult(
            UUID runId,
            CodeRunStatus status,
            Integer exitCode,
            String stdout,
            String stderr,
            Long durationMs
    ) {
        this(runId, status, exitCode, stdout, stderr, durationMs, List.of());
    }
}
