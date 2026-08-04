package com.promptstudio.buildandtest;

/**
 * 채점 테스트 한 건의 결과. JUnit XML 리포트에서 뽑아낸다.
 *
 * @param className  테스트 클래스명. 리포트에 없으면 null
 * @param name       테스트 이름. 문제 저장소가 한글 메서드명을 쓰므로 사실상 요구사항 문장이다
 * @param message    실패·에러 사유 한 줄. 통과·스킵이면 null
 * @param durationMs 케이스 소요 시간. 리포트에 시간이 없으면 null
 */
public record RunCase(
        String className,
        String name,
        RunCaseStatus status,
        String message,
        Long durationMs
) {
}
