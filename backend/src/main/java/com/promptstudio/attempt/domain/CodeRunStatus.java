package com.promptstudio.attempt.domain;

/**
 * 코드 빌드/실행 한 건의 상태.
 *
 * <p>QUEUED만 진행 중이고 나머지는 모두 종료 상태다. QUEUED는 백엔드가 요청을 받을 때 직접 넣는 값이라
 * 워커가 보내는 결과 메시지에는 등장하지 않는다.
 */
public enum CodeRunStatus {

    QUEUED,
    SUCCEEDED,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIMEOUT,
    RUNNER_ERROR;

    public boolean isTerminal() {
        return this != QUEUED;
    }
}
