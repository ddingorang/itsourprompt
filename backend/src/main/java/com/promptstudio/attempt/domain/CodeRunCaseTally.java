package com.promptstudio.attempt.domain;

/**
 * 실행 한 건의 채점 케이스 집계. 케이스 행에서 세어 만든다(컬럼으로 들고 있지 않다).
 *
 * <p>목록 화면이 "2/5 통과" 같은 배지를 그리는 데 쓴다. 케이스 배열 전체를 목록에 실으면
 * stdout을 뺀 이유(응답 무게)를 그대로 반복하게 되므로 집계만 내보낸다.
 *
 * <p>{@code skipped}는 {@code total}에 포함하되 통과로 세지 않는다 — 실행되지 않은 것을 통과로
 * 세면 배지가 거짓이 되고, 아예 빼면 문제 저장소가 테스트를 비활성화한 사실이 화면에서 사라진다.
 */
public record CodeRunCaseTally(
        int total,
        int passed,
        int failed,
        int error,
        int skipped
) {
}
