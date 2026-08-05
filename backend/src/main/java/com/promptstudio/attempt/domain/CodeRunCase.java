package com.promptstudio.attempt.domain;

/**
 * 채점 테스트 한 건의 결과.
 *
 * <p>실행 1건에 여러 행으로 저장하고 집계는 조회 시 파생한다 — {@code attempt_llm_call}과 같은 판단이다
 * (원본 행이 진실이고 합계는 세어서 만든다). 통과 개수를 컬럼으로 들고 있으면 케이스 행과 어긋날 수 있다.
 *
 * @param className  테스트 클래스명. 리포트에 없으면 null
 * @param name       테스트 이름. 문제 저장소가 한글 메서드명을 쓰므로 사실상 요구사항 문장이다
 * @param message    실패·에러 사유 한 줄. 통과·스킵이면 null
 * @param durationMs 케이스 소요 시간. 리포트에 시간이 없으면 null
 */
public record CodeRunCase(
        String className,
        String name,
        CodeRunCaseStatus status,
        String message,
        Long durationMs
) {
}
