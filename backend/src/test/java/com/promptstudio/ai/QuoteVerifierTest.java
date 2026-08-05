package com.promptstudio.ai;

import com.promptstudio.ai.OpenAiFeedbackGenerator.TurnEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteVerifierTest {

    private static final String INPUT = """
            <problem_title>
            주문 취소
            </problem_title>
            <user_prompt turn="1">
            cancel(orderId)로 주문을 취소할 수 있게 해 줘
            </user_prompt>
            <changed_file turn="1" path="src/OrderService.java" type="MODIFIED">
            order.changeStatus(Order.Status.CANCELED);
            </changed_file>
            <user_prompt turn="2">
            배송이 시작된 주문은 취소를 막아 줘
            </user_prompt>
            <changed_file turn="2" path="src/OrderService.java" type="MODIFIED">
            throw new IllegalStateException("배송이 시작된 주문은 취소할 수 없습니다.");
            </changed_file>
            """;

    @Test
    void 입력에_그대로_있는_인용은_어느_수준에서도_어긋나지_않는다() {
        QuoteVerifier.Result result = QuoteVerifier.verify(INPUT, List.of(
                entry("cancel(orderId)로 주문을 취소할 수 있게 해 줘"),
                entry("배송이 시작된 주문은 취소를 막아 줘")));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.rawMisses()).isEmpty();
        assertThat(result.normalizedMisses()).isEmpty();
        assertThat(result.turnScopedMisses()).isEmpty();
        assertThat(result.grounded()).isTrue();
    }

    /**
     * 지어낸 문장이 여기 걸린다 — 이 검증기가 있는 이유다.
     */
    @Test
    void 입력에_없는_문장은_불일치로_잡힌다() {
        QuoteVerifier.Result result = QuoteVerifier.verify(INPUT, List.of(
                entry("사용자가 diff를 열어 확인했어요")));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.normalizedMisses())
                .extracting(QuoteVerifier.Miss::turn, QuoteVerifier.Miss::quote)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, "사용자가 diff를 열어 확인했어요"));
        assertThat(result.grounded()).isFalse();
    }

    /**
     * 줄바꿈과 들여쓰기만 다른 복사는 계약 위반이 아니다 — raw로만 세고 정규화에서는 통과시킨다.
     */
    @Test
    void 공백만_다른_인용은_raw에만_잡히고_정규화에서는_통과한다() {
        QuoteVerifier.Result result = QuoteVerifier.verify(INPUT, List.of(
                entry("cancel(orderId)로   주문을 취소할 수  있게 해 줘  ")));

        assertThat(result.rawMisses()).hasSize(1);
        assertThat(result.normalizedMisses()).isEmpty();
    }

    /**
     * 다른 턴의 문장을 이 턴의 근거로 내놓는 것은 계약 위반은 아니지만 진단 지표로 남긴다.
     */
    @Test
    void 다른_턴의_문장을_인용하면_턴_스코프에서만_잡힌다() {
        QuoteVerifier.Result result = QuoteVerifier.verify(INPUT, List.of(
                entry("배송이 시작된 주문은 취소를 막아 줘"),
                entry("배송이 시작된 주문은 취소를 막아 줘")));

        assertThat(result.normalizedMisses()).isEmpty();
        assertThat(result.turnScopedMisses())
                .extracting(QuoteVerifier.Miss::turn)
                .containsExactly(1);
    }

    @Test
    void 마지막_턴의_스코프는_입력_끝까지다() {
        QuoteVerifier.Result result = QuoteVerifier.verify(INPUT, List.of(
                entry(),
                entry("throw new IllegalStateException(\"배송이 시작된 주문은 취소할 수 없습니다.\");")));

        assertThat(result.turnScopedMisses()).isEmpty();
    }

    @Test
    void 인용이_없는_턴은_아무것도_세지_않는다() {
        QuoteVerifier.Result result = QuoteVerifier.verify(INPUT, List.of(entry(), entry()));

        assertThat(result.total()).isZero();
        assertThat(result.grounded()).isTrue();
    }

    /**
     * 구조화 출력이라도 배열이 통째로 빠져 올 수 있다. null을 통과 처리해 NPE로 제출을 죽이지 않는다.
     */
    @Test
    void quotes가_null이면_건너뛴다() {
        List<TurnEntry> turns = new ArrayList<>();
        turns.add(new TurnEntry(null, "피드백"));
        turns.add(null);

        QuoteVerifier.Result result = QuoteVerifier.verify(INPUT, turns);

        assertThat(result.total()).isZero();
        assertThat(result.grounded()).isTrue();
    }

    @Test
    void 정규화는_연속_공백을_한_칸으로_접고_앞뒤를_턴다() {
        assertThat(QuoteVerifier.normalize("  제약 칸이\n\n  비어 있어요  ")).isEqualTo("제약 칸이 비어 있어요");
        assertThat(QuoteVerifier.normalize(null)).isEmpty();
    }

    private TurnEntry entry(String... quotes) {
        return new TurnEntry(List.of(quotes), "피드백");
    }
}
