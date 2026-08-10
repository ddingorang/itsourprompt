package com.promptstudio.attempt.domain;

import java.util.List;

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
     * 앞 턴이 바꾼 파일을 다음 프롬프트가 이름으로 짚지 않은 턴.
     *
     * <p>키는 긍정형이지만 <b>"못 짚은 턴이 있다"일 때 켜진다</b> — 신호 이름은 관찰하는 행동을
     * 가리키고, 줄이 나가는 조건은 그 행동의 부재다.
     */
    private static final String PROMPT_NAMES_PREVIOUS_CHANGED_FILE = "prompt_names_previous_changed_file";

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
     * 실측으로 고른 문안(s1b)이다. 실호출 128턴에서 요약이 편집 파일을 다 부르는 비율이
     * 대조군 24/32(75.0%) → 32/32(100%)로 Δ+25.0%p였고, 팔당 n=32의 2SE 20.0%p를 넘겼다.
     * 동률이던 s1a와는 부작용이 갈랐다 — 둘 다 2SE 안이라 탈락 사유는 없었으나 s1a가 편집 전
     * 파일 이름 선언을 13.8%p 떨어뜨려, 형식을 지정하지 않은 이쪽을 골랐다. s5a가 같은 자리를
     * 42.9%p 떨어뜨린 것과 부호가 같다.
     *
     * <p><b>처방이 요약을 겨눈다.</b> 신호는 프롬프트가 앞 턴 변경을 안 짚은 것인데 규칙은 AI의
     * 요약에 붙는다 — 사용자가 다음 프롬프트에서 무엇을 짚을지는 앞 턴 요약이 바꾼 파일을
     * 다 보여 줘야 알 수 있어서다. 사용자의 습관을 직접 고치라고 말하지 않는다.
     */
    private static final String NAME_CHANGED_FILES_RULE =
            "작업 요약에는 이번에 바꾼 모든 파일의 이름이 반드시 들어가야 한다. "
                    + "파일 이름 없이 변경 내용을 보고하지 마라.";

    /**
     * 이 세션에 줄 규칙 한 줄. 어느 신호에도 걸리지 않으면 null이고, 그때는 화면에 아무것도 올리지
     * 않는다 — 빈 절을 세우지 않는다.
     *
     * <p><b>여럿 걸려도 줄은 하나다.</b> 지시를 쌓으면 비선형으로 무너지므로 순서를 고정해 하나만
     * 고른다. 순서는 <b>S5 &gt; (S2) &gt; S1</b>이다.
     * <ul>
     *   <li>S5가 최우선인 이유는 실측 Δ가 가장 크고(+75%p) 코어 루프인 실행 확인을 겨누기 때문이다.
     *       덤으로 s5b는 S1이 겨누던 행동도 50% → 83%로 끌어올려 S1 처방을 부분 대체한다.
     *   <li>S2는 <b>측정에서 탈락해 카탈로그에 없다</b>(증분 12.5%p가 분해능 20%p 미달). 재입장하면
     *       S1 앞에 놓는다 — 1턴 세션에서도 켜지고 결함이 상류다. 프롬프트가 아예 파일을 안 부르면
     *       앞 턴 변경을 짚는 습관도 성립할 수 없다.
     * </ul>
     *
     * <p>"이 세션에서 더 강하게 나타난 신호"로 고르는 동적 규칙은 기각했다. 이 값은 조회할 때마다
     * 다시 계산되므로 실행을 더 돌릴 때마다 보이는 줄이 바뀐다.
     *
     * @param turns       어템프트의 턴. 개수가 근거 문장의 분모이고, 프롬프트·변경 목록이 신호의 입력이다
     * @param testResults 이 어템프트에 쌓인 턴별 실행 결과
     */
    public static CarryLine of(List<AttemptView.TurnView> turns, TurnTestResults testResults) {
        int turnCount = turns.size();

        if (turnCount <= 0) {
            return null;
        }

        int unconfirmed = testResults.countTurnsWithoutFinishedRun(turnCount);

        if (unconfirmed > 0) {
            return new CarryLine(TURN_HAS_NO_FINISHED_RUN, RUN_RULE, runReason(turnCount, unconfirmed));
        }

        int unnamed = countTurnsMissingPreviousChange(turns);

        if (unnamed > 0) {
            return new CarryLine(
                    PROMPT_NAMES_PREVIOUS_CHANGED_FILE,
                    NAME_CHANGED_FILES_RULE,
                    previousChangeReason(turnCount, unnamed));
        }

        return null;
    }

    /**
     * 앞 턴이 바꾼 파일을 프롬프트가 하나도 안 짚은 턴 수.
     *
     * <p>첫 턴은 분자에서 빠진다 — 앞 턴이 없어 짚을 것이 없다. 앞 턴이 아무것도 안 바꾼 자리도
     * 빠진다. 둘 다 "안 짚었다"가 아니라 <b>짚을 대상이 없다</b>라서, 세면 습관이 없는 세션에도
     * 줄이 붙는다.
     */
    private static int countTurnsMissingPreviousChange(List<AttemptView.TurnView> turns) {
        int unnamed = 0;

        for (int index = 1; index < turns.size(); index++) {
            List<FileChange> changes = turns.get(index - 1).changes();

            if (changes.isEmpty()) {
                continue;
            }

            if (!namesAny(turns.get(index).userPrompt(), changes)) {
                unnamed++;
            }
        }

        return unnamed;
    }

    /** 하나라도 짚었으면 짚은 것으로 본다. 전부 나열하라는 요구는 프롬프트가 아니라 요약에 건다. */
    private static boolean namesAny(String prompt, List<FileChange> changes) {
        for (FileChange change : changes) {
            if (PromptFileNames.names(prompt, change.path())) {
                return true;
            }
        }

        return false;
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

    /**
     * 첫 턴이 분자에서 빠지므로 분자는 분모에 닿을 수 없다. 그래서 "N턴 중 N턴" 자리를 따로 두지 않는다.
     */
    private static String previousChangeReason(int turnCount, int unnamed) {
        return ("%d턴 중 %d턴에서 앞 턴이 바꾼 파일을 프롬프트가 짚지 않았어요."
                + " AI가 요약에 바꾼 파일을 전부 나열하면 다음 프롬프트에서 무엇을 짚을지 바로 보여요.")
                .formatted(turnCount, unnamed);
    }
}
