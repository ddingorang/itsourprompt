package com.promptstudio.buildandtest;

/**
 * 워커가 돌려줄 수 있는 종료 상태. 백엔드 {@code CodeRunStatus}의 부분집합이다
 * (QUEUED는 백엔드만 쓰므로 여기에 없다).
 */
public enum RunStatus {

    SUCCEEDED,
    COMPILE_ERROR,
    /** 컴파일도 실행도 됐지만 채점 테스트가 깨졌다. 사용자가 고쳐야 하는 유일한 실패다. */
    TEST_FAILED,
    RUNTIME_ERROR,
    TIMEOUT,
    RUNNER_ERROR
}
