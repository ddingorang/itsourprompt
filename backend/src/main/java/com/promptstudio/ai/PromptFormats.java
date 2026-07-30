package com.promptstudio.ai;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.List;

/**
 * 코드 생성 프롬프트의 파일 블록 포맷. 한 프롬프트 안에서 파일 표기가 갈리면 AI가 두 표기를 다른 형식으로 읽으므로
 * 코드 생성 경로의 파일 블록은 모두 여기를 지난다.
 *
 * <p>피드백 프롬프트는 신뢰 불가 데이터를 태그로 격리하는 자체 포맷을 쓰므로 이 포맷을 공유하지 않는다.
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
