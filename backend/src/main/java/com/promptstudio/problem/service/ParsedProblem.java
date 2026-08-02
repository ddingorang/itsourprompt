package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.List;

/**
 * @param files     사용자에게 주는 스켈레톤. 어템프트의 시작 파일이 된다.
 * @param testFiles 채점용 테스트. 사용자와 AI 어디에도 노출하지 않고 워커로만 보낸다.
 */
record ParsedProblem(
        String slug,
        String title,
        String specMd,
        List<ProblemFile> files,
        List<ProblemFile> testFiles
) {
}
