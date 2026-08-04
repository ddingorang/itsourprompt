package com.promptstudio.ai;

/**
 * pattern 피드백 응답을 구조화 출력으로 강제하는 JSON 스키마.
 *
 * <p>봉투는 {@link FeedbackSchema}와 같은 {@code turnFeedbacks} + {@code overall}이다. 별도 호출이라
 * 이름이 겹쳐도 섞이지 않고, 두 응답이 같은 파서를 탄다.
 *
 * <p>턴 피드백 개수는 여기서도 minItems/maxItems로 못 박는다. description만으론 모델이 개수를 자주 어긴다.
 *
 * <p>개수 요구가 스키마에 들어가므로 호출마다 새로 만든다.
 */
final class PatternSchema {

    private static final String TEMPLATE = """
            {
              "type": "object",
              "properties": {
                "turnFeedbacks": {
                  "type": "array",
                  "description": "정확히 %1$d개. 턴 순서대로의 피드백이며, 각 항목은 이 턴의 패턴·쓸 기법 두 절의 한국어 Markdown.",
                  "minItems": %1$d,
                  "maxItems": %1$d,
                  "items": {
                    "type": "string"
                  }
                },
                "overall": {
                  "type": "string",
                  "description": "세션 전체에 붙인 이름과 다음 세션에 가져갈 기법의 한국어 Markdown."
                }
              },
              "required": ["turnFeedbacks", "overall"],
              "additionalProperties": false
            }
            """;

    private PatternSchema() {
    }

    static String jsonSchema(int turnCount) {
        return TEMPLATE.formatted(turnCount);
    }
}
