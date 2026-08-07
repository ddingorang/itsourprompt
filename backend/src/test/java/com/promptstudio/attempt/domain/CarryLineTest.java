package com.promptstudio.attempt.domain;

import com.promptstudio.attempt.domain.TurnTestResults.Graded;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CarryLineTest {

    @Test
    void 실행_기록이_하나도_없으면_모든_턴이_신호에_걸린다() {
        CarryLine carry = CarryLine.of(4, TurnTestResults.EMPTY);

        assertThat(carry).isNotNull();
        assertThat(carry.signal()).isEqualTo("turn_has_no_finished_run");
        assertThat(carry.reason()).contains("이번 세션의 4턴에서 한 번도");
    }

    @Test
    void 일부_턴만_실행했으면_안_돌린_턴만_센다() {
        CarryLine carry = CarryLine.of(3, results(graded(0, CodeRunStatus.SUCCEEDED)));

        assertThat(carry).isNotNull();
        assertThat(carry.reason()).contains("3턴 중 2턴에서");
    }

    @Test
    void 모든_턴을_실행했으면_줄을_주지_않는다() {
        CarryLine carry = CarryLine.of(
                2, results(graded(0, CodeRunStatus.TEST_FAILED), graded(1, CodeRunStatus.SUCCEEDED)));

        assertThat(carry).isNull();
    }

    /**
     * 컴파일에 실패했어도 사용자는 자기 코드에 대해 무언가를 봤다. 확인을 안 한 턴이 아니다 —
     * {@link TurnTestResults.TurnTestResult#ran()}의 판단을 그대로 따른다.
     */
    @Test
    void 컴파일_실패도_실행한_턴이다() {
        CarryLine carry = CarryLine.of(1, results(graded(0, CodeRunStatus.COMPILE_ERROR)));

        assertThat(carry).isNull();
    }

    /**
     * 사용자가 본 것이 코드의 동작이 아니라 채점 인프라의 사고다. 확인이 된 턴으로 세면 안 된다.
     */
    @Test
    void RUNNER_ERROR로_끝난_턴은_확인하지_않은_턴이다() {
        CarryLine carry = CarryLine.of(1, results(graded(0, CodeRunStatus.RUNNER_ERROR)));

        assertThat(carry).isNotNull();
    }

    @Test
    void 아직_끝나지_않은_실행은_확인으로_치지_않는다() {
        CarryLine carry = CarryLine.of(1, results(graded(0, CodeRunStatus.QUEUED)));

        assertThat(carry).isNotNull();
    }

    /**
     * 턴이 없는 어템프트는 제출될 수 없지만, 규칙 줄은 세션을 근거로 조립하므로 분모가 0이면 문장이
     * 성립하지 않는다. 걸리지 않는 쪽으로 닫는다.
     */
    @Test
    void 턴이_없으면_줄을_주지_않는다() {
        assertThat(CarryLine.of(0, TurnTestResults.EMPTY)).isNull();
    }

    /**
     * 실측으로 고른 문안은 s5b다. 준수율에서 s5a와 12/12 동률이었고 부작용이 갈랐다 —
     * s5a는 편집 전 파일 이름 선언을 42.9%p 떨어뜨렸다. 이 줄은 사용자 설정 파일에 영구히 남으므로
     * 다른 행동을 덜 건드리는 쪽을 골랐다. 문안을 바꾸려면 준수율을 다시 재야 한다.
     */
    @Test
    void 규칙_문장은_실측으로_고른_s5b다() {
        CarryLine carry = CarryLine.of(1, TurnTestResults.EMPTY);

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

        assertThat(CarryLine.of(2, results)).isNotNull();
    }

    private TurnTestResults results(Graded... graded) {
        return TurnTestResults.of(List.of(graded), null);
    }

    private Graded graded(Integer turnOrdinal, CodeRunStatus status) {
        return new Graded(turnOrdinal, status, null, List.of());
    }
}
