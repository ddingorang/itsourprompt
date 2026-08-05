package com.promptstudio.ai;

/**
 * 피드백 응답을 구조화 출력으로 강제하는 JSON 스키마.
 *
 * <p>Spring AI가 JSON_SCHEMA 응답 형식에 strict=true를 고정하므로, 모든 object는
 * additionalProperties=false를 갖고 모든 속성이 required에 있어야 한다.
 *
 * <p>턴 피드백 개수는 minItems/maxItems로 못 박는다. description으로만 요구하던 동안 모델이 개수를 자주
 * 어겼다 — 6턴 세션 실패율 17%, 12턴 세션은 8/8 전부 실패했고, 부족(1 of 12)과 초과(15 of 12) 양쪽으로 어긋났다.
 * strict 모드가 배열 개수 제약을 거부할 것을 우려해 빼 두었으나, 실제로는 400 없이 받아들이며 20턴까지 개수가
 * 어긋나지 않는다. 파서와 도메인 {@code Attempt.submit}의 검증은 안전망으로 그대로 둔다.
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
                  "description": "정확히 %1$d개. 턴 순서대로의 피드백이며, 각 항목은 요약 두 문장과 프롬프트 정리하기·결과와 비교하기·다음 프롬프트 쓰기 세 절의 한국어 Markdown.",
                  "minItems": %1$d,
                  "maxItems": %1$d,
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
