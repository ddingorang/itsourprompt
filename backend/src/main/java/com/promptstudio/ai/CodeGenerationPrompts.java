package com.promptstudio.ai;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.domain.Turn;
import com.promptstudio.problem.domain.ProblemFile;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

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

                대화에는 이전 요청과 작업 요약이 포함될 수 있으며, 반드시 마지막 메시지의 [현재 프로젝트 파일]을 기준으로 수정하세요.
                files에는 수정하지 않은 기존 파일도 반드시 모두 포함해야 합니다.
                삭제할 파일은 files에서 제외합니다.
                파일 경로는 상대 경로만 사용하고, 절대 경로나 .. 경로는 사용하지 마세요.
                """;
    }

    static List<Message> messages(Attempt attempt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt()));

        for (Turn turn : attempt.turns()) {
            messages.add(new UserMessage(turn.userPrompt()));
            messages.add(new AssistantMessage(turn.aiSummary()));
        }

        messages.add(new UserMessage(currentStatePrompt(attempt.currentFiles(), userPrompt)));

        return List.copyOf(messages);
    }

    private static String currentStatePrompt(List<ProblemFile> files, String userPrompt) {
        StringBuilder message = new StringBuilder();
        message.append("\n\n[현재 프로젝트 파일]\n");

        for (ProblemFile file : files) {
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
