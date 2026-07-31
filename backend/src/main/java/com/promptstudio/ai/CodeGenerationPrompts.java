package com.promptstudio.ai;

import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.problem.domain.ProblemView;
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
                문제 명세와 사용자 요청에 맞게 프로젝트 파일을 툴로 직접 탐색하고 수정하세요.

                사용할 수 있는 툴은 셋뿐입니다.
                - %1$s: 현재 프로젝트의 모든 파일 경로를 반환합니다.
                - %2$s: 파일 하나의 현재 전체 내용을 반환합니다.
                - %3$s: 기존 파일의 내용을 통째로 교체합니다. 일부만 보내면 나머지 내용은 사라집니다.

                파일 내용은 이 대화에 들어 있지 않습니다. 반드시 %1$s와 %2$s로 현재 상태를 먼저 확인하세요.
                %3$s은 이미 있는 파일만 수정할 수 있습니다. 새 파일 생성과 파일 삭제는 불가능합니다.
                수정하기 전에 어떻게 구현할지 생각해 설명하고, 그 방식대로 파일을 고치세요.

                작업을 마치면 수행한 내용을 한국어 일반 텍스트로 요약해 답하세요.
                최종 답변에는 JSON이나 코드 블록을 넣지 마세요.
                """.formatted(ToolCallEntry.LIST_FILES, ToolCallEntry.READ_FILE, ToolCallEntry.EDIT_FILE);
    }

    static List<Message> messages(ProblemView problem, AttemptView attempt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt()));

        for (AttemptView.TurnView turn : attempt.turns()) {
            messages.add(new UserMessage(turn.userPrompt()));
            messages.add(new AssistantMessage(historySummary(turn)));
        }

        messages.add(new UserMessage(currentRequestPrompt(problem, userPrompt)));

        return List.copyOf(messages);
    }

    /**
     * 과거 툴콜은 재생하지 않는다. 옛 read_file 결과는 현재 상태와 모순될 수 있어 경로만 힌트로 남긴다.
     */
    private static String historySummary(AttemptView.TurnView turn) {
        if (turn.changes().isEmpty()) {
            return turn.aiSummary();
        }

        List<String> paths = new ArrayList<>();

        for (FileChange change : turn.changes()) {
            paths.add(change.path());
        }

        return turn.aiSummary() + "\n[수정한 파일] " + String.join(", ", paths);
    }

    private static String currentRequestPrompt(ProblemView problem, String userPrompt) {
        return "[문제 명세]\n" + problem.specMd() + "\n\n[사용자 요청]\n" + userPrompt;
    }
}
