package com.promptstudio.ai;

/**
 * 피드백 응답을 구조화 출력으로 강제하는 JSON 스키마.
 *
 * <p>Spring AI가 JSON_SCHEMA 응답 형식에 strict=true를 고정하므로, 모든 object는
 * additionalProperties=false를 갖고 모든 속성이 required에 있어야 한다.
 *
 * <p>턴 피드백 개수는 minItems/maxItems가 아니라 description으로 요구한다 — strict 모드의 배열 개수 제약
 * 지원이 확인되지 않아 스키마로 걸면 매 제출이 실패할 수 있다. 스키마는 개수를 강제하지 않고,
 * {@code OpenAiFeedbackGenerator}의 파서가 응답을 검증하며 도메인 {@code Attempt.submit}이 불변식으로 다시 지킨다.
 *
 * <p>개수 요구가 스키마에 들어가므로 호출마다 새로 만든다.
 */
final class FeedbackSchema {

    private static final String TEMPLATE = """
            {
              "type": "object",
              "properties": {
                "turnFeedbacks": {
                  "type": "array",
                  "description": "정확히 %d개. 턴 순서대로의 피드백이며, 각 항목은 변환·대조·처방 세 절의 한국어 Markdown.",
                  "items": {
                    "type": "string"
                  }
                },
                "overall": {
                  "type": "string",
                  "description": "세션 전체에 대한 한국어 Markdown 피드백."
                }
              },
              "required": ["turnFeedbacks", "overall"],
              "additionalProperties": false
            }
            """;

    private FeedbackSchema() {
    }

    static String jsonSchema(int turnCount) {
        return TEMPLATE.formatted(turnCount);
    }
}
