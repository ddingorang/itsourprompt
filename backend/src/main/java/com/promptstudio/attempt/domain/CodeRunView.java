package com.promptstudio.attempt.domain;

import java.util.UUID;

/**
 * 조회용 코드 실행 기록. 아직 끝나지 않았으면 exitCode·stdout·stderr·durationMs는 모두 null이다.
 */
public record CodeRunView(
        UUID id,
        Long attemptId,
        CodeRunStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        Long durationMs
) {

    public static CodeRunView queued(UUID id, Long attemptId) {
        return new CodeRunView(id, attemptId, CodeRunStatus.QUEUED, null, null, null, null);
    }
}
