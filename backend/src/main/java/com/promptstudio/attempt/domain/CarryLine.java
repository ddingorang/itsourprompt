package com.promptstudio.attempt.domain;

/**
 * 제출한 세션에서 반복된 습관 하나를 골라, 다음 문제의 상시 지시 파일(<code>AGENTS.md</code>류)에
 * 붙여넣을 <b>규칙 한 줄</b>을 돌려준다. 근거 문장도 함께 준다.
 *
 * <p><b>LLM은 여기 개입하지 않는다.</b> 신호 판정도, 문안 선택도, 근거 문장 조립도 전부 코드가 한다.
 * 이 리포지토리가 두 번 배운 것이라 그렇다 — pattern 렌즈의 세션 이름을 모델에게 맡겼더니 계산값을
 * 주고도 호출의 40%에서 이름을 통째로 빼먹었고(그래서 {@code PatternPrompts.renderTurn}이 제목 줄을
 * 조립한다), 같은 세션의 방식 판정은 모델을 바꿀 때마다 뒤집혔다(그래서
 * {@code PatternPrompts.namesPreviousChange}가 대조를 코드로 한다). 산출물 전체가 계산이면 검증할
 * LLM이 아예 없고, 피드백 프롬프트를 통째로 갈아도 이 줄은 무사하다.
 *
 * <p>그래서 이 값은 두 렌즈의 마크다운에 섞이지 않고 피드백 응답의 <b>별도 필드</b>로 나간다.
 *
 * @param signal   이 줄을 고른 신호의 키. 화면에 쓰지 않고 나중에 도달률을 되짚을 때 쓴다
 * @param rule   지시 파일에 그대로 붙여넣을 규칙 한 줄. 도구 중립이라 파일 이름도 도구 이름도 담지 않는다.
 *               <b>평문이다</b> — 사용자가 그대로 복사해 가므로 마크업이 섞이면 붙여넣기가 망가진다
 * @param reason 왜 이 줄인지. 계산한 값으로 조립하는 평문 한 문장이다
 */
public record CarryLine(String signal, String rule, String reason) {

    /**
     * 확인하지 않고 넘어간 턴. {@code TurnTestResults}로 계산하며, 실행이 아예 없는 턴과
     * 끝났지만 채점 인프라가 사고 난 턴을 함께 센다.
     */
    private static final String TURN_HAS_NO_FINISHED_RUN = "turn_has_no_finished_run";

    /**
     * 실측으로 고른 문안(s5b)이다. 실호출 84턴에서 대조군 25% → 100%(Δ+75%p)였고, 동률이던 s5a는
     * 형식을 지정한 탓에 편집 전 파일 이름 선언을 42.9%p 떨어뜨려 탈락했다. 이 줄은 사용자의 설정
     * 파일에 영구히 남으므로 다른 행동을 덜 건드리는 쪽을 골랐다.
     *
     * <p><b>문안을 고치려면 준수율을 다시 재야 한다</b> — 근거는 {@code backend/docs/carry-line-design.md},
     * 하네스는 {@code LiveCarryLineHarnessTest}다.
     */
    private static final String RUN_RULE =
            "무엇을 바꿨다고만 말하지 마라. "
                    + "그 변경이 실제로 동작하는지 사람이 확인할 방법을 요약에 반드시 함께 적어라.";

    /**
     * 이 세션에 줄 규칙 한 줄. 어느 신호에도 걸리지 않으면 null이고, 그때는 화면에 아무것도 올리지
     * 않는다 — 빈 절을 세우지 않는다.
     *
     * <p>카탈로그가 아직 하나라 신호도 하나만 본다. 신호를 늘리려면 여기에 분기를 더하고 우선순위를
     * 정하면 된다. 자격은 <b>준수를 코드가 확인할 수 있는 줄만 싣는다</b>이다.
     *
     * @param turnCount   어템프트의 턴 수. 근거 문장의 분모다
     * @param testResults 이 어템프트에 쌓인 턴별 실행 결과
     */
    public static CarryLine of(int turnCount, TurnTestResults testResults) {
        if (turnCount <= 0) {
            return null;
        }

        int unconfirmed = testResults.countTurnsWithoutFinishedRun(turnCount);

        if (unconfirmed == 0) {
            return null;
        }

        return new CarryLine(TURN_HAS_NO_FINISHED_RUN, RUN_RULE, runReason(turnCount, unconfirmed));
    }

    /**
     * 분모와 분자를 그대로 문장에 넣는다. 전부 안 돌린 세션에 "N턴 중 N턴"이라고 쓰면 셈이 맞아도
     * 읽기에 걸리므로 그 자리만 문장을 바꾼다.
     */
    private static String runReason(int turnCount, int unconfirmed) {
        String counted = unconfirmed == turnCount
                ? "이번 세션의 %d턴에서 한 번도 코드를 실행해 결과를 확인하지 않으셨어요.".formatted(turnCount)
                : "%d턴 중 %d턴에서 코드를 실행해 결과를 확인하지 않으셨어요.".formatted(turnCount, unconfirmed);

        return counted + " AI가 확인 방법을 요약에 함께 적어 두면 다음엔 무엇을 돌려 봐야 할지 바로 알 수 있어요.";
    }
}
