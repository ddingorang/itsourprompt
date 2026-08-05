package com.promptstudio.buildandtest;

/**
 * 채점 테스트 한 건의 결과.
 *
 * <p>JUnit 리포트의 {@code <testcase>} 자식 요소로 판정한다 — {@code failure}면 단정 실패,
 * {@code error}면 테스트가 예외로 죽은 것, {@code skipped}면 실행되지 않은 것이고, 자식이 없으면 통과다.
 * FAILED와 ERROR를 합치지 않는다: 앞은 코드가 기대와 다르게 동작한 것이고 뒤는 테스트가 아예 못 돈 것이라
 * 사용자가 봐야 할 곳이 다르다.
 */
public enum RunCaseStatus {

    PASSED,
    FAILED,
    ERROR,
    SKIPPED
}
