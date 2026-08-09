package com.promptstudio.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 모델이 근거로 내놓은 인용이 정말 입력에 있었는지 대조한다.
 *
 * <p>지어내기를 막는 것은 지시가 아니라 이 대조다. 프롬프트로 "없는 사실을 쓰지 마라"고 적어도 모델은
 * 그럴듯한 문장을 만들어 내는데, 인용을 필수 필드로 받으면 그 문장이 입력에 있었는지를 코드가 셀 수 있다.
 *
 * <p>세 수준을 함께 잰다. raw는 한 글자도 다르지 않은 복사고, normalized는 연속 공백을 한 칸으로 접고
 * 앞뒤를 턴 것이며, turnScoped는 그 인용이 <b>그 턴의</b> 태그 블록 안에 있었는지다. 계약으로 쓰는 것은
 * normalized 하나뿐이고 — 줄바꿈과 들여쓰기가 다르다는 이유로 제출을 실패시키는 것은 값이 없다 —
 * 나머지 둘은 섀도 로그의 진단 지표다.
 *
 * <p>빈 인용은 세 수준 모두에서 불일치다. 인용을 아예 안 내놓은 턴(빈 배열)은 스키마가 허용하지만,
 * 빈 문자열을 인용이라고 내놓은 것은 다르다 — 대조를 통과시키면 게이트를 무력화하는 자리라서 막는다.
 */
final class QuoteVerifier {

    private QuoteVerifier() {
    }

    /**
     * @param turn 1-based 턴 번호. 프롬프트의 turn 속성과 같다
     */
    record Miss(int turn, String quote) {
    }

    /**
     * @param total 대조한 인용의 총 개수. 인용이 하나도 없는 응답이면 0이다
     */
    record Result(
            int total,
            List<Miss> rawMisses,
            List<Miss> normalizedMisses,
            List<Miss> turnScopedMisses
    ) {

        boolean grounded() {
            return normalizedMisses.isEmpty();
        }
    }

    static Result verify(String input, List<OpenAiFeedbackGenerator.TurnEntry> turns) {
        String normalizedInput = normalize(input);
        int total = 0;
        List<Miss> rawMisses = new ArrayList<>();
        List<Miss> normalizedMisses = new ArrayList<>();
        List<Miss> turnScopedMisses = new ArrayList<>();

        for (int index = 0; index < turns.size(); index++) {
            int turnNumber = index + 1;
            OpenAiFeedbackGenerator.TurnEntry entry = turns.get(index);

            if (entry == null || entry.quotes() == null) {
                continue;
            }

            String normalizedScope = normalize(turnScope(input, turnNumber));

            for (String quote : entry.quotes()) {
                total++;

                String normalizedQuote = normalize(quote);

                // 빈 인용은 대조가 아니라 세 수준을 전부 그냥 통과한다 — contains("")는 언제나 참이다.
                // 근거를 하나도 내놓지 않은 것이므로 통과가 아니라 불일치로 센다.
                if (normalizedQuote.isEmpty()) {
                    rawMisses.add(new Miss(turnNumber, quote));
                    normalizedMisses.add(new Miss(turnNumber, quote));
                    turnScopedMisses.add(new Miss(turnNumber, quote));
                    continue;
                }

                if (!input.contains(quote)) {
                    rawMisses.add(new Miss(turnNumber, quote));
                }

                if (!normalizedInput.contains(normalizedQuote)) {
                    normalizedMisses.add(new Miss(turnNumber, quote));
                }

                if (!normalizedScope.contains(normalizedQuote)) {
                    turnScopedMisses.add(new Miss(turnNumber, quote));
                }
            }
        }

        return new Result(
                total, List.copyOf(rawMisses), List.copyOf(normalizedMisses), List.copyOf(turnScopedMisses));
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", " ").trim();
    }

    /**
     * 한 턴의 태그 블록. 프롬프트는 턴을 순서대로 이어 붙이므로 이 턴의 첫 {@code turn="N"}에서 다음 턴의
     * 첫 {@code turn="N+1"} 직전까지가 그 턴의 자리다. 마지막 턴이면 입력 끝까지다.
     *
     * <p>턴 번호는 따옴표까지 함께 찾는다 — {@code turn="1"}이 {@code turn="10"}을 집으면 안 된다.
     */
    private static String turnScope(String input, int turnNumber) {
        if (input == null) {
            return "";
        }

        int start = input.indexOf(turnAttribute(turnNumber));

        if (start < 0) {
            return "";
        }

        int end = input.indexOf(turnAttribute(turnNumber + 1), start);

        return end < 0 ? input.substring(start) : input.substring(start, end);
    }

    private static String turnAttribute(int turnNumber) {
        return "turn=\"" + turnNumber + "\"";
    }
}
