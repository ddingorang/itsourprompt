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
                  "description": "정확히 %1$d개. 턴 순서대로의 피드백.",
                  "minItems": %1$d,
                  "maxItems": %1$d,
                  "items": {
                    "type": "object",
                    "properties": {
                      "quotes": {
                        "type": "array",
                        "minItems": 0,
                        "maxItems": 5,
                        "items": { "type": "string" },
                        "description": "이 턴의 판정을 뒷받침하는 근거. 이 턴의 입력 태그 안 문장을 한 글자도 바꾸지 말고 그대로 복사한다. 짚을 것이 없는 턴은 빈 배열."
                      },
                      "name": {
                        "type": "string",
                        "enum": ["vibe coding", "human review", ""],
                        "description": "이 턴의 이름. next_prompt_names_changed_file 태그의 named 값을 그대로 옮긴다 — true면 human review, false면 vibe coding. 그 턴의 줄이 없는 마지막 턴만 빈 문자열."
                      },
                      "gloss": {
                        "type": "string",
                        "description": "이 턴에 맞춘 이름의 한국어 뜻풀이 한 줄. 사전 문장을 그대로 베끼지 않는다. 이름이 빈 문자열이면 이것도 빈 문자열."
                      },
                      "feedback": {
                        "type": "string",
                        "description": "근거 문장들과 필요하면 `### 쓸 기법` 절. `### 이 턴의 패턴` 제목과 이름 줄은 쓰지 않는다 — 그건 BE가 붙인다."
                      }
                    },
                    "required": ["quotes", "name", "gloss", "feedback"],
                    "additionalProperties": false
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
