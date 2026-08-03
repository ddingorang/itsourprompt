package com.promptstudio.attempt.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 목록 조회용 실행 요약.
 *
 * <p>stdout·stderr를 담지 않는 것이 이 타입의 존재 이유다. 두 값은 각각 워커 상한인 64KB까지 커질 수
 * 있어(ProcessCodeExecutor의 max-output-bytes) 실행이 쌓인 어템프트의 목록에 실으면 응답이
 * 메가바이트 단위가 된다. 본문이 필요하면 {@link CodeRunView}를 돌려주는 단건 조회를 쓴다.
 *
 * @param turnOrdinal 실행한 코드가 몇 번째 턴의 것인지(0-based). 스켈레톤 원본을 실행했거나
 *                    턴 단위 기록 이전에 만들어진 행이면 null이다.
 * @param exitCode    아직 끝나지 않은 실행이거나 타임아웃으로 종료 코드가 없으면 null
 * @param finishedAt  아직 끝나지 않은 실행(QUEUED)이면 null
 */
public record CodeRunSummary(
        UUID id,
        Integer turnOrdinal,
        CodeRunStatus status,
        Integer exitCode,
        Long durationMs,
        Instant createdAt,
        Instant finishedAt
) {
}
