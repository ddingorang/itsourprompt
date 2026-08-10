package com.promptstudio.attempt.domain;

/**
 * 프롬프트가 파일 하나를 이름으로 불렀는지 대조한다. 세 표기를 다 본다 — 전체 경로, 파일명,
 * 확장자를 뗀 이름. 사람은 {@code src/main/java/com/shop/OrderService.java}보다
 * {@code OrderService}라고 쓴다.
 *
 * <p><b>같은 규약의 사본이 셋이다.</b> 정본은 {@code PatternPrompts.namesPreviousChange}이고
 * 측정용 사본이 {@code CarryLineChecks.namesFile}, 이것이 셋째다. 합치지 않은 이유 둘:
 * <ul>
 *   <li>정본은 {@code com.promptstudio.ai} 패키지에 있고 이 클래스는 {@code attempt.domain}의
 *       패키지 프라이빗이다. 서로 부를 수 없고, 부르게 만들려면 도메인이 어댑터 패키지에 기대게 된다.
 *   <li>{@code PatternPrompts.java}는 설계 문서의 명시 기각 표가 <b>무접촉</b>으로 못 박은 파일이다.
 * </ul>
 *
 * <p><b>측정용 사본은 일부러 고치지 않는다.</b> {@code CarryLineChecks}는 아래 경계 규칙 없이
 * 후행 경계만 본다. 그쪽을 지금 고치면 이미 기록된 준수율
 * ({@code backend/docs/measurements/carry-s1s2-20260807.jsonl})을 원문에서 재채점할 때 숫자가
 * 달라져, 커밋해 둔 원자료가 그 판정을 더는 재현하지 못한다. 다만 이 차이가 그 측정의 결론을
 * 바꾸지는 않는다 — 고친 규칙으로 D1을 전건 재채점해도 네 팔의 통과 수가 하나도 안 움직였다.
 */
final class PromptFileNames {

    private PromptFileNames() {
    }

    /**
     * {@code text}가 {@code path}를 이름으로 부르는가.
     *
     * <p>경로가 비면 부를 이름이 없어 거짓이다. 텍스트가 null이면 아무 이름도 안 부른 것으로 본다 —
     * 프롬프트가 빈 턴은 신호를 켜는 쪽이 맞다.
     */
    static boolean names(String text, String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        String haystack = text == null ? "" : text;
        String fileName = path.substring(path.lastIndexOf('/') + 1);

        if (haystack.contains(path) || haystack.contains(fileName)) {
            return true;
        }

        int dot = fileName.lastIndexOf('.');

        // dot < 0은 확장자가 없는 파일, dot == 0은 `.env` 같은 닷파일이다. 뗄 이름이 없다 —
        // 빈 이름으로 대조하면 아무 텍스트나 그 파일을 부른 것이 된다.
        if (dot <= 0) {
            return false;
        }

        return namesWhole(haystack, fileName.substring(0, dot));
    }

    /**
     * 이름이 <b>낱말째로</b> 나오는가. 부분 문자열로 세면 {@code OrderService}만 적은 프롬프트가
     * {@code Order.java}도 부른 것이 된다 — 실측에서 실제로 나온 오탐이다.
     *
     * <p>앞뒤를 <b>모두</b> 본다. 뒤만 보면 {@code PurchaseOrder}가 {@code Order}를 부른 것이 된다.
     */
    private static boolean namesWhole(String haystack, String bareName) {
        int from = 0;

        while (true) {
            int at = haystack.indexOf(bareName, from);

            if (at < 0) {
                return false;
            }

            if (isBoundary(haystack, at - 1) && isBoundary(haystack, at + bareName.length())) {
                return true;
            }

            from = at + 1;
        }
    }

    /**
     * 경계는 <b>ASCII 낱자·숫자·밑줄이 아닌 것</b>이다. 한글을 낱자로 치면 안 된다 — 한국어는 이름
     * 뒤에 조사가 바로 붙어(`Order를`) 그것까지 경계가 아니라고 보면 정상 호출이 전부 빠진다.
     * 반대로 밑줄은 경계가 아니다({@code Order_v2}는 다른 이름이다).
     */
    private static boolean isBoundary(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return true;
        }

        char character = text.charAt(index);

        return !(character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '_');
    }
}
