package com.promptstudio.relay.service;

import com.promptstudio.attempt.domain.CodeRunCase;
import com.promptstudio.attempt.domain.CodeRunCaseStatus;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 실행 결과를 릴레이 점수 계산이 쓰는 형태로 접은 것.
 *
 * <p>집계 규칙: 케이스가 있으면 PASSED를 센다. 케이스가 없으면 컴파일 실패·타임아웃 등 코드가
 * 테스트에 못 미친 것이므로 0개 통과다 — 앞사람이 통과시킨 테스트를 깨뜨렸다는 사실이 델타에
 * 음수로 드러나야 한다. 단 RUNNER_ERROR는 코드가 아니라 채점 인프라의 실패라 통과 수를 알 수
 * 없으므로 null로 남긴다. null을 0으로 뭉개면 인프라 사고가 주자의 감점으로 둔갑한다.
 */
public record RelayGradeTally(
        CodeRunStatus runStatus,
        Integer passed,
        Integer total,
        List<RelayGradingFinished.FailedCase> failedCases
) {

    public static RelayGradeTally from(CodeRunResult result) {
        if (result.status() == CodeRunStatus.RUNNER_ERROR) {
            return new RelayGradeTally(result.status(), null, null, List.of());
        }

        int passed = 0;
        List<RelayGradingFinished.FailedCase> failed = new ArrayList<>();

        for (CodeRunCase testCase : result.cases()) {
            if (testCase.status() == CodeRunCaseStatus.PASSED) {
                passed++;
            } else if (testCase.status() != CodeRunCaseStatus.SKIPPED) {
                failed.add(new RelayGradingFinished.FailedCase(testCase.name(), testCase.message()));
            }
        }

        return new RelayGradeTally(result.status(), passed, result.cases().size(), List.copyOf(failed));
    }
}
