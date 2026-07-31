package com.promptstudio.attempt.domain;

import java.util.UUID;

/**
 * 워커가 돌려준 실행 결과. 전송 계층(rabbit)의 메시지를 도메인으로 번역한 형태다.
 */
public record CodeRunResult(
        UUID runId,
        CodeRunStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        Long durationMs
) {
}
