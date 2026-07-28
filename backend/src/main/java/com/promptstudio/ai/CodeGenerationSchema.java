package com.promptstudio.ai;

/**
 * 코드 생성 응답을 구조화 출력으로 강제하는 JSON 스키마.
 *
 * <p>Spring AI가 JSON_SCHEMA 응답 형식에 strict=true를 고정하므로, 모든 object는
 * additionalProperties=false를 갖고 모든 속성이 required에 있어야 한다.
 * 형태는 {@link GeneratedCodeParser}가 역직렬화하는 구조와 일치해야 한다.
 */
final class CodeGenerationSchema {

    static final String JSON = """
            {
              "type": "object",
              "properties": {
                "files": {
                  "type": "array",
                  "description": "수정 여부와 무관하게 프로젝트의 최종 파일 전체.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "path": {
                        "type": "string",
                        "description": "프로젝트 루트 기준 상대 경로."
                      },
                      "content": {
                        "type": "string",
                        "description": "파일의 최종 전체 내용."
                      }
                    },
                    "required": ["path", "content"],
                    "additionalProperties": false
                  }
                },
                "aiResponse": {
                  "type": "string",
                  "description": "이번 턴에서 수행한 작업에 대한 한국어 요약."
                }
              },
              "required": ["files", "aiResponse"],
              "additionalProperties": false
            }
            """;

    private CodeGenerationSchema() {
    }
}
