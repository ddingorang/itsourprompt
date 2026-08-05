package com.promptstudio.buildandtest;

import java.util.List;

/**
 * 한 번의 빌드/실행 결과. exitCode는 프로세스가 정상 종료했을 때만 값이 있다
 * (타임아웃으로 강제 종료했거나 실행 단계에 도달하지 못했으면 null).
 *
 * @param cases 채점 테스트를 돌린 경우의 케이스별 결과. 테스트 없이 main만 실행했거나
 *              컴파일 실패·타임아웃으로 리포트가 생기지 않았으면 빈 목록이다.
 *              {@code status}는 여전히 종료 코드로 판정하며 이 목록이 그것을 뒤집지 않는다.
 */
public record RunOutcome(
        RunStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        long durationMs,
        List<RunCase> cases
) {

    /** 케이스가 없는 결과(테스트 미실행·컴파일 실패 등). 기존 호출부를 그대로 두기 위한 생성자다. */
    RunOutcome(RunStatus status, Integer exitCode, String stdout, String stderr, long durationMs) {
        this(status, exitCode, stdout, stderr, durationMs, List.of());
    }

    static RunOutcome failure(RunStatus status, String stderr, long durationMs) {
        return new RunOutcome(status, null, "", stderr, durationMs);
    }

    RunOutcome withCases(List<RunCase> cases) {
        return new RunOutcome(status, exitCode, stdout, stderr, durationMs, cases);
    }
}
