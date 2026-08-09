package com.promptstudio.attempt.domain;

import com.promptstudio.attempt.domain.TurnTestResults.Graded;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CarryLineTest {

    private static final String ORDER_SERVICE = "src/main/java/com/shop/OrderService.java";

    @Test
    void 실행_기록이_하나도_없으면_모든_턴이_신호에_걸린다() {
        CarryLine carry = CarryLine.of(turns(4), TurnTestResults.EMPTY);

        assertThat(carry).isNotNull();
        assertThat(carry.signal()).isEqualTo("turn_has_no_finished_run");
        assertThat(carry.reason()).contains("이번 세션의 4턴에서 한 번도");
    }

    @Test
    void 일부_턴만_실행했으면_안_돌린_턴만_센다() {
        CarryLine carry = CarryLine.of(turns(3), results(graded(0, CodeRunStatus.SUCCEEDED)));

        assertThat(carry).isNotNull();
        assertThat(carry.reason()).contains("3턴 중 2턴에서");
    }

    @Test
    void 모든_턴을_실행했으면_줄을_주지_않는다() {
        CarryLine carry = CarryLine.of(
                turns(2), results(graded(0, CodeRunStatus.TEST_FAILED), graded(1, CodeRunStatus.SUCCEEDED)));

        assertThat(carry).isNull();
    }

    /**
     * 컴파일에 실패했어도 사용자는 자기 코드에 대해 무언가를 봤다. 확인을 안 한 턴이 아니다 —
     * {@link TurnTestResults.TurnTestResult#ran()}의 판단을 그대로 따른다.
     */
    @Test
    void 컴파일_실패도_실행한_턴이다() {
        CarryLine carry = CarryLine.of(turns(1), results(graded(0, CodeRunStatus.COMPILE_ERROR)));

        assertThat(carry).isNull();
    }

    /**
     * 사용자가 본 것이 코드의 동작이 아니라 채점 인프라의 사고다. 확인이 된 턴으로 세면 안 된다.
     */
    @Test
    void RUNNER_ERROR로_끝난_턴은_확인하지_않은_턴이다() {
        CarryLine carry = CarryLine.of(turns(1), results(graded(0, CodeRunStatus.RUNNER_ERROR)));

        assertThat(carry).isNotNull();
    }

    @Test
    void 아직_끝나지_않은_실행은_확인으로_치지_않는다() {
        CarryLine carry = CarryLine.of(turns(1), results(graded(0, CodeRunStatus.QUEUED)));

        assertThat(carry).isNotNull();
    }

    /**
     * 턴이 없는 어템프트는 제출될 수 없지만, 규칙 줄은 세션을 근거로 조립하므로 분모가 0이면 문장이
     * 성립하지 않는다. 걸리지 않는 쪽으로 닫는다.
     */
    @Test
    void 턴이_없으면_줄을_주지_않는다() {
        assertThat(CarryLine.of(turns(0), TurnTestResults.EMPTY)).isNull();
    }

    /**
     * 실측으로 고른 문안은 s5b다. 준수율에서 s5a와 12/12 동률이었고 부작용이 갈랐다 —
     * s5a는 편집 전 파일 이름 선언을 42.9%p 떨어뜨렸다. 이 줄은 사용자 설정 파일에 영구히 남으므로
     * 다른 행동을 덜 건드리는 쪽을 골랐다. 문안을 바꾸려면 준수율을 다시 재야 한다.
     */
    @Test
    void 규칙_문장은_실측으로_고른_s5b다() {
        CarryLine carry = CarryLine.of(turns(1), TurnTestResults.EMPTY);

        assertThat(carry.rule()).isEqualTo(
                "무엇을 바꿨다고만 말하지 마라. "
                        + "그 변경이 실제로 동작하는지 사람이 확인할 방법을 요약에 반드시 함께 적어라.");
    }

    /**
     * 베이스라인은 스켈레톤 원본 실행이라 어느 턴에도 붙지 않는다. 그것으로 턴을 확인한 것으로 세면
     * 사용자가 아무것도 안 돌렸는데 신호가 꺼진다.
     */
    @Test
    void 스켈레톤_베이스라인은_턴을_확인한_것이_아니다() {
        TurnTestResults results = TurnTestResults.of(List.of(), graded(null, CodeRunStatus.TEST_FAILED));

        assertThat(CarryLine.of(turns(2), results)).isNotNull();
    }

    /**
     * S1 채택 문안은 s1b다. D1이 s1a와 32/32 동률이라 부작용이 갈랐고, 형식을 지정하지 않은
     * 쪽이 편집 전 파일 이름 선언을 덜 떨어뜨렸다.
     */
    @Test
    void 앞_턴이_바꾼_파일을_안_짚으면_S1이_켜진다() {
        CarryLine carry = CarryLine.of(
                List.of(turn("취소 기능 만들어 줘", ORDER_SERVICE), turn("예외도 던져 줘")),
                allRan(2));

        assertThat(carry).isNotNull();
        assertThat(carry.signal()).isEqualTo("prompt_names_previous_changed_file");
        assertThat(carry.rule()).isEqualTo(
                "작업 요약에는 이번에 바꾼 모든 파일의 이름이 반드시 들어가야 한다. "
                        + "파일 이름 없이 변경 내용을 보고하지 마라.");
        assertThat(carry.reason()).startsWith("2턴 중 1턴에서 앞 턴이 바꾼 파일을");
    }

    @Test
    void 프롬프트가_앞_턴_변경_파일을_부르면_S1이_꺼진다() {
        CarryLine carry = CarryLine.of(
                List.of(turn("취소 기능 만들어 줘", ORDER_SERVICE), turn("OrderService에 예외도 던져 줘")),
                allRan(2));

        assertThat(carry).isNull();
    }

    /**
     * 앞 턴이 아무것도 안 바꿨으면 짚을 대상이 없다. 세면 습관이 없는 세션에도 줄이 붙는다.
     */
    @Test
    void 앞_턴에_변경이_없으면_S1_판정에서_빠진다() {
        CarryLine carry = CarryLine.of(
                List.of(turn("이 코드 설명해 줘"), turn("그럼 예외를 던져 줘")),
                allRan(2));

        assertThat(carry).isNull();
    }

    /**
     * 첫 턴은 앞 턴이 없어 짚을 것이 없다. 분자에 넣으면 1턴 세션이 전부 걸린다.
     */
    @Test
    void 첫_턴은_S1_판정_대상이_아니다() {
        CarryLine carry = CarryLine.of(List.of(turn("취소 기능 만들어 줘", ORDER_SERVICE)), allRan(1));

        assertThat(carry).isNull();
    }

    /**
     * 줄은 하나만 나간다. 둘 다 걸리면 실측 Δ가 큰 쪽(S5)이 이긴다.
     */
    @Test
    void S5와_S1이_같이_걸리면_S5를_보여준다() {
        CarryLine carry = CarryLine.of(
                List.of(turn("취소 기능 만들어 줘", ORDER_SERVICE), turn("예외도 던져 줘")),
                TurnTestResults.EMPTY);

        assertThat(carry).isNotNull();
        assertThat(carry.signal()).isEqualTo("turn_has_no_finished_run");
    }

    private TurnTestResults results(Graded... graded) {
        return TurnTestResults.of(List.of(graded), null);
    }

    /** 모든 턴을 실행한 상태. S5를 꺼서 뒤 순위 신호를 드러낸다. */
    private TurnTestResults allRan(int turnCount) {
        List<Graded> graded = new ArrayList<>();

        for (int ordinal = 0; ordinal < turnCount; ordinal++) {
            graded.add(graded(ordinal, CodeRunStatus.SUCCEEDED));
        }

        return TurnTestResults.of(graded, null);
    }

    private Graded graded(Integer turnOrdinal, CodeRunStatus status) {
        return new Graded(turnOrdinal, status, null, List.of());
    }

    /**
     * 변경이 <b>비어 있는</b> 턴을 만든다. S5만 재는 기존 테스트에 뒤 순위 신호가 공진하면 안 된다.
     */
    private List<AttemptView.TurnView> turns(int count) {
        List<AttemptView.TurnView> turns = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            turns.add(turn("프롬프트"));
        }

        return turns;
    }

    private AttemptView.TurnView turn(String userPrompt, String... changedPaths) {
        List<FileChange> changes = new ArrayList<>();

        for (String path : changedPaths) {
            changes.add(new FileChange(path, FileChange.ChangeType.MODIFIED, "내용"));
        }

        return new AttemptView.TurnView(userPrompt, null, changes, List.of(), null, null, null);
    }
}
