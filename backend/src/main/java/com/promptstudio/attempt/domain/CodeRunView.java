package com.promptstudio.attempt.domain;

import java.util.List;
import java.util.UUID;

/**
 * 조회용 코드 실행 기록. 아직 끝나지 않았으면 exitCode·stdout·stderr·durationMs는 모두 null이다.
 *
 * @param turnOrdinal 실행한 코드가 몇 번째 턴의 것인지(0-based). 스켈레톤 원본을 실행했거나
 *                    턴 단위 기록 이전에 만들어진 행이면 null이다.
 * @param cases       채점 케이스별 결과. 테스트를 돌리지 않은 실행이거나 케이스 기록 이전의 실행이면
 *                    빈 목록이다. 빈 목록은 "테스트 0개 통과"가 아니라 "케이스 기록이 없음"이며,
 *                    통과 여부의 진실은 {@code status}다.
 */
public record CodeRunView(
        UUID id,
        Long attemptId,
        Integer turnOrdinal,
        CodeRunStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        Long durationMs,
        List<CodeRunCase> cases
) {

    /** 케이스를 아직 붙이지 않은 조회 결과. 저장소가 행만 읽어 만들 때 쓴다. */
    public CodeRunView(
            UUID id,
            Long attemptId,
            Integer turnOrdinal,
            CodeRunStatus status,
            Integer exitCode,
            String stdout,
            String stderr,
            Long durationMs
    ) {
        this(id, attemptId, turnOrdinal, status, exitCode, stdout, stderr, durationMs, List.of());
    }

    public static CodeRunView queued(UUID id, Long attemptId, Integer turnOrdinal) {
        return new CodeRunView(id, attemptId, turnOrdinal, CodeRunStatus.QUEUED, null, null, null, null);
    }

    public CodeRunView withCases(List<CodeRunCase> cases) {
        return new CodeRunView(
                id, attemptId, turnOrdinal, status, exitCode, stdout, stderr, durationMs, cases);
    }
}
