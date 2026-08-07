package com.promptstudio.ai;

import com.promptstudio.ai.CarryLineChecks.EditAudit;
import com.promptstudio.ai.CarryLineChecks.Event;
import com.promptstudio.ai.CarryLineChecks.Verdict;
import com.promptstudio.attempt.domain.ToolCallEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CarryLineChecksTest {

    private static final String ORDER_SERVICE = "src/main/java/com/shop/OrderService.java";
    private static final String INVENTORY = "src/main/java/com/shop/Inventory.java";

    /** 마무리 호출이 실패하거나 최종 텍스트가 비었을 때 어댑터가 채우는 문자열. */
    private static final String FALLBACK_SUMMARY = "작업을 완료했지만 AI가 요약을 제공하지 않았습니다.";

    @Test
    void 확장자를_뗀_이름만_불러도_요약이_그_파일을_담은_것으로_센다() {
        assertThat(CarryLineChecks.summaryNamesEveryEditedFile(
                List.of(ORDER_SERVICE), "OrderService에 cancel을 추가했습니다.")).isTrue();
    }

    @Test
    void 편집한_두_파일_중_하나를_요약이_빠뜨리면_실패한다() {
        assertThat(CarryLineChecks.summaryNamesEveryEditedFile(
                List.of(ORDER_SERVICE, INVENTORY), "OrderService에 cancel을 추가했습니다.")).isFalse();
    }

    @Test
    void 편집이_없으면_요약이_담을_것도_없어_통과한다() {
        assertThat(CarryLineChecks.summaryNamesEveryEditedFile(List.of(), "아무것도 고치지 않았습니다."))
                .isTrue();
    }

    @Test
    void 대체_요약은_경로를_하나도_담지_않아_D1을_실패한다() {
        assertThat(CarryLineChecks.summaryNamesEveryEditedFile(List.of(ORDER_SERVICE), FALLBACK_SUMMARY))
                .isFalse();
    }

    @Test
    void 선언하지_않고_편집하면_announced가_false다() {
        List<Event> events = List.of(
                new Event(1, "바로 고치겠습니다.", null, null),
                new Event(1, null, ToolCallEntry.EDIT_FILE, ORDER_SERVICE));

        assertThat(CarryLineChecks.auditEdits(events, "취소 기능을 만들어 줘"))
                .extracting(EditAudit::announced)
                .containsExactly(false);
    }

    @Test
    void 편집_직전까지의_텍스트가_파일을_부르면_announced가_true다() {
        List<Event> events = List.of(
                new Event(1, "수정 대상: OrderService", null, null),
                new Event(1, null, ToolCallEntry.EDIT_FILE, ORDER_SERVICE));

        assertThat(CarryLineChecks.auditEdits(events, "취소 기능을 만들어 줘"))
                .extracting(EditAudit::announced)
                .containsExactly(true);
    }

    /**
     * 뒤늦은 선언이 앞선 편집을 소급해 준수로 만들면 안 된다 — 그 편집 시점에는 아직 안 밝힌 것이다.
     */
    @Test
    void 첫_편집_뒤에_선언하면_첫_편집은_announced가_false로_남는다() {
        List<Event> events = List.of(
                new Event(1, null, ToolCallEntry.EDIT_FILE, ORDER_SERVICE),
                new Event(2, "OrderService를 고쳤고 이어서 Inventory도 고칩니다.", null, null),
                new Event(2, null, ToolCallEntry.EDIT_FILE, ORDER_SERVICE));

        assertThat(CarryLineChecks.auditEdits(events, "취소 기능을 만들어 줘"))
                .extracting(EditAudit::announced)
                .containsExactly(false, true);
    }

    @Test
    void 읽지_않고_편집하면_readFirst가_false다() {
        List<Event> events = List.of(
                new Event(1, null, ToolCallEntry.READ_FILE, INVENTORY),
                new Event(1, null, ToolCallEntry.EDIT_FILE, INVENTORY),
                new Event(1, null, ToolCallEntry.EDIT_FILE, ORDER_SERVICE));

        assertThat(CarryLineChecks.auditEdits(events, "재고도 되돌려 줘"))
                .extracting(EditAudit::path, EditAudit::readFirst)
                .containsExactly(tuple(INVENTORY, true), tuple(ORDER_SERVICE, false));
    }

    @Test
    void 편집_뒤에_읽으면_그_편집은_readFirst가_false로_남는다() {
        List<Event> events = List.of(
                new Event(1, null, ToolCallEntry.EDIT_FILE, ORDER_SERVICE),
                new Event(2, null, ToolCallEntry.READ_FILE, ORDER_SERVICE),
                new Event(3, null, ToolCallEntry.EDIT_FILE, ORDER_SERVICE));

        assertThat(CarryLineChecks.auditEdits(events, "취소 기능을 만들어 줘"))
                .extracting(EditAudit::readFirst)
                .containsExactly(false, true);
    }

    @Test
    void list_files는_대상이_없어_감사에서_무시한다() {
        List<Event> events = List.of(
                new Event(1, null, ToolCallEntry.LIST_FILES, null),
                new Event(1, null, ToolCallEntry.EDIT_FILE, ORDER_SERVICE));

        assertThat(CarryLineChecks.auditEdits(events, "취소 기능을 만들어 줘")).hasSize(1);
    }

    @Test
    void 편집이_한_건도_없으면_감사가_비어_있다() {
        List<Event> events = List.of(
                new Event(1, "구조를 설명하겠습니다.", null, null),
                new Event(1, null, ToolCallEntry.LIST_FILES, null),
                new Event(1, null, ToolCallEntry.READ_FILE, ORDER_SERVICE));

        assertThat(CarryLineChecks.auditEdits(events, "구조 설명해줘")).isEmpty();
    }

    @Test
    void 프롬프트가_부른_파일인지를_withinPrompt에_남긴다() {
        List<Event> events = List.of(
                new Event(1, null, ToolCallEntry.EDIT_FILE, ORDER_SERVICE),
                new Event(2, null, ToolCallEntry.EDIT_FILE, INVENTORY));

        assertThat(CarryLineChecks.auditEdits(events, "OrderService만 고쳐."))
                .extracting(EditAudit::withinPrompt)
                .containsExactly(true, false);
    }

    @Test
    void 라운드_번호를_감사에_그대로_싣는다() {
        List<Event> events = List.of(
                new Event(2, null, ToolCallEntry.EDIT_FILE, ORDER_SERVICE),
                new Event(5, null, ToolCallEntry.EDIT_FILE, INVENTORY));

        assertThat(CarryLineChecks.auditEdits(events, "고쳐 줘"))
                .extracting(EditAudit::round)
                .containsExactly(2, 5);
    }

    @Test
    void 세_표기를_모두_돌려준다() {
        assertThat(CarryLineChecks.nameForms(ORDER_SERVICE))
                .containsExactly(ORDER_SERVICE, "OrderService.java", "OrderService");
    }

    @Test
    void 경로가_없으면_어느_대조도_통과하지_못한다() {
        assertThat(CarryLineChecks.nameForms(null)).isEmpty();
    }

    @Test
    void 확인을_아예_말하지_않은_요약은_FAIL이다() {
        assertThat(CarryLineChecks.summaryStatesHowToCheck("OrderService에 cancel을 추가했습니다."))
                .isEqualTo(Verdict.FAIL);
    }

    @Test
    void 대체_요약은_확인_낱말이_없어_D5도_실패한다() {
        assertThat(CarryLineChecks.summaryStatesHowToCheck(FALLBACK_SUMMARY)).isEqualTo(Verdict.FAIL);
    }

    @Test
    void 자기가_확인했다는_보고뿐이면_FAIL이다() {
        assertThat(CarryLineChecks.summaryStatesHowToCheck(
                "cancel을 추가했습니다.\n취소하면 상태가 바뀌는 것을 확인했습니다."))
                .isEqualTo(Verdict.FAIL);
    }

    @Test
    void 독자에게_방법을_주면_PASS다() {
        assertThat(CarryLineChecks.summaryStatesHowToCheck(
                "cancel을 추가했습니다.\n확인하려면 cancel을 부른 뒤 quantityOf 값이 늘었는지 보시면 됩니다."))
                .isEqualTo(Verdict.PASS);
    }

    /**
     * 이 테스트는 원래 {@code 확인할 수 있습니다}를 PASS로 단언해 <b>오탐을 못 박고 있었다.</b>
     * 실측이 그 어미를 걷어내게 만들었으므로(같은 이름의 아래 테스트 참고) 의도만 남기고 어미를
     * 진짜 지시형으로 바꾼다 — 이 테스트가 지키려던 것은 "접두사 없이도 PASS가 난다"이지
     * "{@code 수 있}이 PASS다"가 아니었다.
     */
    @Test
    void 접두사가_아니라_어미로_PASS를_가른다() {
        assertThat(CarryLineChecks.summaryStatesHowToCheck(
                "무엇을 바꿨는지는 아래와 같습니다.\n재고가 복구되는지 quantityOf로 확인해 보세요."))
                .isEqualTo(Verdict.PASS);
    }

    /**
     * 접두사를 지시한 문안이 내는 모양이다. 진짜 확인 방법인데 어미가 없어 기계가 못 가른다 —
     * 접두사로 채점하지 않기로 했으므로 여기서 통과시키지 않고 사람에게 넘긴다.
     */
    @Test
    void 어미가_없는_안내_줄은_UNSCORED로_남긴다() {
        assertThat(CarryLineChecks.summaryStatesHowToCheck(
                "cancel을 추가했습니다.\n확인 방법: cancel 호출 후 quantityOf 값 비교"))
                .isEqualTo(Verdict.UNSCORED);
    }

    @Test
    void 빈_요약은_읽을_것이_없어_UNSCORED다() {
        assertThat(CarryLineChecks.summaryStatesHowToCheck("   ")).isEqualTo(Verdict.UNSCORED);
        assertThat(CarryLineChecks.summaryStatesHowToCheck(null)).isEqualTo(Verdict.UNSCORED);
    }

    /**
     * `수 없`을 `수 있`으로 읽으면 못 했다는 보고가 방법 안내로 뒤집힌다.
     */
    @Test
    void 확인하지_못했다는_문장을_PASS로_읽지_않는다() {
        assertThat(CarryLineChecks.summaryStatesHowToCheck(
                "외부 라이브러리가 없어 테스트를 실행할 수 없습니다."))
                .isNotEqualTo(Verdict.PASS);
    }

    /**
     * 실측이 잡은 오탐. {@code 확인할 수 있도록 수정했습니다}는 <b>코드가 무엇을 하게 됐는지</b>
     * 보고하는 문장이지 독자에게 주는 확인 방법이 아니다. 84턴에서 PASS 38건 중 16건이 이 꼴이었고
     * 대조군의 통과 3건이 전부 여기 해당했다.
     */
    @Test
    void 확인할_수_있도록_고쳤다는_보고는_PASS가_아니다() {
        assertThat(CarryLineChecks.summaryStatesHowToCheck(
                "취소 응답에서 취소 후 남은 재고 수량을 확인할 수 있도록 수정했습니다."))
                .isNotEqualTo(Verdict.PASS);
    }

    @Test
    void 확인할_수_있습니다로_끝나는_설명도_PASS가_아니다() {
        assertThat(CarryLineChecks.summaryStatesHowToCheck(
                "- 취소 응답에서 다음 정보를 확인할 수 있습니다."))
                .isNotEqualTo(Verdict.PASS);
    }

    /**
     * 실측이 잡은 오탐. 스켈레톤에 {@code Order}와 {@code OrderService}가 함께 있어
     * 확장자를 뗀 이름을 부분 문자열로 세면 {@code OrderService}만 적힌 요약이 {@code Order.java}도
     * 부른 것으로 잡힌다.
     */
    @Test
    void 긴_이름_안에_짧은_이름이_들어있어도_짧은_쪽을_부른_것으로_세지_않는다() {
        assertThat(CarryLineChecks.summaryNamesEveryEditedFile(
                List.of("src/main/java/com/shop/Order.java"),
                "OrderService와 OrderController를 고쳤습니다."))
                .isFalse();
    }

    @Test
    void 짧은_이름을_단독으로_부르면_센다() {
        assertThat(CarryLineChecks.summaryNamesEveryEditedFile(
                List.of("src/main/java/com/shop/Order.java"),
                "Order의 상태 전이를 고쳤습니다."))
                .isTrue();
    }

    @Test
    void 확장자를_붙인_파일명은_그대로_대조한다() {
        assertThat(CarryLineChecks.summaryNamesEveryEditedFile(
                List.of("src/main/java/com/shop/Order.java"),
                "Order.java를 고쳤습니다."))
                .isTrue();
    }
}
