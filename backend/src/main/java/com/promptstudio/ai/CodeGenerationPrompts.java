package com.promptstudio.ai;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;

final class CodeGenerationPrompts {

    private CodeGenerationPrompts() {
    }

    static String systemPrompt() {
        return """
                당신은 Java 코드 생성 도우미입니다.
                사용자 요청과 문제 명세에 맞게 제공된 프로젝트 파일을 수정하세요.
                반드시 아래 JSON 객체만 반환하세요. Markdown 코드 블록이나 JSON 외의 문장은 금지합니다.

                {
                  "files": [
                    { "path": "상대 경로", "content": "파일 전체 내용" }
                  ],
                  "aiResponse": "수행한 작업을 한국어로 짧게 요약"
                }

                files에는 수정하지 않은 기존 파일도 반드시 모두 포함해야 합니다.
                삭제할 파일은 files에서 제외합니다.
                파일 경로는 상대 경로만 사용하고, 절대 경로나 .. 경로는 사용하지 마세요.
                """;
    }

    static String userPrompt(Problem problem, String userPrompt) {
        StringBuilder message = new StringBuilder();
        message.append("\n\n[현재 프로젝트 파일]\n");

        for (ProblemFile file : problem.files()) {
            message.append("--- ")
                    .append(file.path())
                    .append(" ---\n")
                    .append(file.content())
                    .append("\n");
        }

        message.append("\n[사용자 요청]\n")
                .append(userPrompt);

        return message.toString();
    }
}
