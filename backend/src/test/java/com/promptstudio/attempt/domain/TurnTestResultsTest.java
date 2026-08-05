package com.promptstudio.attempt.domain;

import com.promptstudio.attempt.domain.TurnTestResults.Graded;
import com.promptstudio.attempt.domain.TurnTestResults.TurnTestResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TurnTestResultsTest {

    @Test
    void 첫_턴의_델타는_베이스라인이_기준이다() {
        TurnTestResults results = TurnTestResults.of(
                List.of(graded(0, CodeRunStatus.TEST_FAILED, tally(4, 3))),
                graded(null, CodeRunStatus.TEST_FAILED, tally(4, 1)));

        assertThat(results.baselinePassed()).isEqualTo(1);
        assertThat(results.baselineTotal()).isEqualTo(4);
        assertThat(results.forTurn(0))
                .extracting(TurnTestResult::passed, TurnTestResult::delta, TurnTestResult::deltaBaseOrdinal)
                .containsExactly(3, 2, null);
    }

    @Test
    void 둘째_턴부터는_직전_턴이_기준이다() {
        TurnTestResults results = TurnTestResults.of(
                List.of(
                        graded(0, CodeRunStatus.TEST_FAILED, tally(7, 3)),
                        graded(1, CodeRunStatus.SUCCEEDED, tally(7, 7))),
                null);

        assertThat(results.forTurn(1))
                .extracting(TurnTestResult::passed, TurnTestResult::delta, TurnTestResult::deltaBaseOrdinal)
                .containsExactly(7, 4, 0);
    }

    /**
     * RUNNER_ERROR는 코드가 아니라 채점 인프라의 실패다. 통과 수를 0으로 뭉개면 인프라 사고가
     * 사용자의 감점으로 둔갑한다.
     */
    @Test
    void RUNNER_ERROR는_통과_수를_모르는_턴이라_델타도_없다() {
        TurnTestResults results = TurnTestResults.of(
                List.of(
                        graded(0, CodeRunStatus.TEST_FAILED, tally(7, 3)),
                        graded(1, CodeRunStatus.RUNNER_ERROR, null),
                        graded(2, CodeRunStatus.TEST_FAILED, tally(7, 4))),
                null);

        assertThat(results.forTurn(1))
                .extracting(TurnTestResult::passed, TurnTestResult::total, TurnTestResult::delta)
                .containsExactly(null, null, null);
        // 채점을 얻지 못한 턴을 건너뛰고 가장 가까운 이전 턴과 비교한다.
        assertThat(results.forTurn(2))
                .extracting(TurnTestResult::delta, TurnTestResult::deltaBaseOrdinal)
                .containsExactly(1, 0);
    }

    /**
     * 컴파일 실패·타임아웃은 코드가 테스트에 못 미친 것이라 0개 통과다 — 앞 턴이 통과시킨 것을
     * 깨뜨린 사실이 음수 델타로 드러나야 한다.
     */
    @Test
    void 케이스가_없는_종료_상태는_0개_통과고_델타는_음수로_남는다() {
        TurnTestResults results = TurnTestResults.of(
                List.of(
                        graded(0, CodeRunStatus.TEST_FAILED, tally(7, 5)),
                        graded(1, CodeRunStatus.COMPILE_ERROR, null)),
                null);

        assertThat(results.forTurn(1))
                .extracting(TurnTestResult::passed, TurnTestResult::total, TurnTestResult::delta)
                .containsExactly(0, 0, -5);
    }

    @Test
    void 기준이_아예_없으면_델타는_null이다() {
        TurnTestResults results = TurnTestResults.of(
                List.of(graded(0, CodeRunStatus.TEST_FAILED, tally(7, 3))),
                null);

        assertThat(results.forTurn(0))
                .extracting(TurnTestResult::delta, TurnTestResult::deltaBaseOrdinal)
                .containsExactly(null, null);
    }

    /**
     * 실행이 없는 턴은 통과 0건이 아니라 기록 없음이다. 목록에 자리를 만들지 않는다.
     */
    @Test
    void 실행이_없는_턴은_목록에_없다() {
        TurnTestResults results = TurnTestResults.of(
                List.of(
                        graded(0, CodeRunStatus.SUCCEEDED, tally(3, 3)),
                        graded(2, CodeRunStatus.SUCCEEDED, tally(3, 3))),
                null);

        assertThat(results.forTurn(1)).isNull();
        assertThat(results.turns()).extracting(TurnTestResult::turnOrdinal).containsExactly(0, 2);
    }

    /**
     * 앞 턴이 없으면 베이스라인이 기준인데, 건너뛴 턴이 있어도 규칙은 같다.
     */
    @Test
    void 건너뛴_턴이_있어도_기준은_가장_가까운_이전_결과다() {
        TurnTestResults results = TurnTestResults.of(
                List.of(
                        graded(0, CodeRunStatus.TEST_FAILED, tally(5, 2)),
                        graded(3, CodeRunStatus.SUCCEEDED, tally(5, 5))),
                graded(null, CodeRunStatus.TEST_FAILED, tally(5, 1)));

        assertThat(results.forTurn(3))
                .extracting(TurnTestResult::delta, TurnTestResult::deltaBaseOrdinal)
                .containsExactly(3, 0);
    }

    @Test
    void 실행_기록이_없으면_비어_있다() {
        assertThat(TurnTestResults.EMPTY.isEmpty()).isTrue();
        assertThat(TurnTestResults.of(List.of(), null).isEmpty()).isTrue();
    }

    @Test
    void 실패한_테스트_이름을_그대로_들고_있는다() {
        TurnTestResults results = TurnTestResults.of(
                List.of(new Graded(
                        0,
                        CodeRunStatus.TEST_FAILED,
                        tally(3, 1),
                        List.of("배송_시작된_주문은_취소할_수_없다", "취소하면_재고가_복구된다"))),
                null);

        assertThat(results.forTurn(0).failedTestNames())
                .containsExactly("배송_시작된_주문은_취소할_수_없다", "취소하면_재고가_복구된다");
    }

    private Graded graded(Integer turnOrdinal, CodeRunStatus status, CodeRunCaseTally tally) {
        return new Graded(turnOrdinal, status, tally, List.of());
    }

    private CodeRunCaseTally tally(int total, int passed) {
        return new CodeRunCaseTally(total, passed, total - passed, 0, 0);
    }
}
