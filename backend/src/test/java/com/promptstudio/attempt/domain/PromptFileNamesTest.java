package com.promptstudio.attempt.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptFileNamesTest {

    private static final String ORDER = "src/main/java/com/shop/Order.java";

    @Test
    void 전체_경로로_부르면_참() {
        assertThat(PromptFileNames.names("src/main/java/com/shop/Order.java를 고쳐 줘", ORDER)).isTrue();
    }

    @Test
    void 파일명으로_부르면_참() {
        assertThat(PromptFileNames.names("Order.java에 취소를 붙여 줘", ORDER)).isTrue();
    }

    @Test
    void 확장자를_뗀_이름으로_부르면_참() {
        assertThat(PromptFileNames.names("Order에 상태를 하나 더 넣어 줘", ORDER)).isTrue();
    }

    /**
     * 스켈레톤에 {@code Order}와 {@code OrderService}가 함께 있으면 부분 문자열 대조가
     * {@code OrderService}만 짚은 프롬프트를 {@code Order.java}도 짚은 것으로 센다.
     * 실측에서 실제로 나온 오탐이라 회귀로 못 박는다.
     */
    @Test
    void 다른_이름의_앞부분과_겹쳐도_부른_것으로_치지_않는다() {
        assertThat(PromptFileNames.names("OrderService에 cancel을 추가해 줘", ORDER)).isFalse();
    }

    /**
     * 뒤만 보면 {@code PurchaseOrder}가 {@code Order}를 부른 것이 된다. 앞뒤를 모두 봐야 한다.
     */
    @Test
    void 다른_이름의_뒷부분과_겹쳐도_부른_것으로_치지_않는다() {
        assertThat(PromptFileNames.names("PurchaseOrder에 필드를 더해 줘", ORDER)).isFalse();
    }

    /**
     * 밑줄은 경계가 아니다 — {@code Order_v2}는 {@code Order}와 다른 이름이다.
     */
    @Test
    void 밑줄로_이어진_이름은_다른_이름이다() {
        assertThat(PromptFileNames.names("Order_v2를 고쳐 줘", ORDER)).isFalse();
    }

    /**
     * 한국어는 이름 뒤에 조사가 바로 붙는다. 조사를 경계로 안 치면 정상 호출이 전부 빠진다.
     */
    @Test
    void 이름_뒤에_조사가_붙어도_부른_것이다() {
        assertThat(PromptFileNames.names("Order를 고쳐 줘", ORDER)).isTrue();
        assertThat(PromptFileNames.names("Order에 상태를 넣어 줘", ORDER)).isTrue();
    }

    /**
     * {@code .env}는 뗄 이름이 없다. 빈 이름으로 대조하면 아무 텍스트나 그 파일을 부른 것이 된다.
     */
    @Test
    void 닷파일은_뗄_이름이_없어_아무_텍스트도_부르지_않는다() {
        assertThat(PromptFileNames.names("아무 상관 없는 말", "config/.env")).isFalse();
        assertThat(PromptFileNames.names(".env를 고쳐 줘", "config/.env")).isTrue();
    }

    @Test
    void 경로가_없으면_거짓() {
        assertThat(PromptFileNames.names("Order를 고쳐 줘", null)).isFalse();
        assertThat(PromptFileNames.names("Order를 고쳐 줘", " ")).isFalse();
    }

    /**
     * 프롬프트가 빈 턴은 어느 파일도 안 부른 것으로 본다 — 신호를 켜는 쪽으로 닫는다.
     */
    @Test
    void 텍스트가_없으면_아무_이름도_부르지_않은_것이다() {
        assertThat(PromptFileNames.names(null, ORDER)).isFalse();
    }
}
