package com.promptstudio.attempt.domain;

/**
 * 채점 테스트 한 건의 결과.
 *
 * <p>워커가 JUnit 리포트에서 판정해 문자열로 실어 보내고, 알 수 없는 값은 저장 단계에서 거른다.
 *
 * <p>FAILED와 ERROR를 합치지 않는다 — 앞은 코드가 기대와 다르게 동작한 것이고 뒤는 테스트가 예외로
 * 죽어 아예 돌지 못한 것이라, 사용자가 봐야 할 곳이 다르다.
 */
public enum CodeRunCaseStatus {

    PASSED,
    FAILED,
    ERROR,
    SKIPPED
}
