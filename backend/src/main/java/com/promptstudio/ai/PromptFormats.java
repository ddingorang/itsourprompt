package com.promptstudio.ai;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.List;

/**
 * 코드 생성 프롬프트와 피드백 프롬프트가 같은 파일 블록 포맷을 쓴다. 포맷이 갈리면 AI가 두 표기를 다른 형식으로 읽는다.
 */
final class PromptFormats {

    private PromptFormats() {
    }

    static void appendFiles(StringBuilder message, List<ProblemFile> files) {
        for (ProblemFile file : files) {
            message.append("--- ")
                    .append(file.path())
                    .append(" ---\n")
                    .append(file.content())
                    .append("\n");
        }
    }
}
