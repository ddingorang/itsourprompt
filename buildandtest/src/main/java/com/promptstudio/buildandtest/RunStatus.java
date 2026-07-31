package com.promptstudio.buildandtest;

/**
 * 워커가 돌려줄 수 있는 종료 상태. 백엔드 {@code CodeRunStatus}의 부분집합이다
 * (QUEUED는 백엔드만 쓰므로 여기에 없다).
 */
public enum RunStatus {

    SUCCEEDED,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIMEOUT,
    RUNNER_ERROR
}
