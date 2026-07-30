package com.promptstudio.buildandtest;

/**
 * 한 번의 빌드/실행 결과. exitCode는 프로세스가 정상 종료했을 때만 값이 있다
 * (타임아웃으로 강제 종료했거나 실행 단계에 도달하지 못했으면 null).
 */
public record RunOutcome(
        RunStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        long durationMs
) {

    static RunOutcome failure(RunStatus status, String stderr, long durationMs) {
        return new RunOutcome(status, null, "", stderr, durationMs);
    }
}
