package com.promptstudio.attempt.domain;

import java.util.UUID;

/**
 * 조회용 코드 실행 기록. 아직 끝나지 않았으면 exitCode·stdout·stderr·durationMs는 모두 null이다.
 *
 * @param turnOrdinal 실행한 코드가 몇 번째 턴의 것인지(0-based). 스켈레톤 원본을 실행했거나
 *                    턴 단위 기록 이전에 만들어진 행이면 null이다.
 */
public record CodeRunView(
        UUID id,
        Long attemptId,
        Integer turnOrdinal,
        CodeRunStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        Long durationMs
) {

    public static CodeRunView queued(UUID id, Long attemptId, Integer turnOrdinal) {
        return new CodeRunView(id, attemptId, turnOrdinal, CodeRunStatus.QUEUED, null, null, null, null);
    }
}
