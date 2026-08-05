package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.CodeRunCase;
import com.promptstudio.attempt.domain.CodeRunCaseStatus;
import com.promptstudio.attempt.domain.CodeRunCaseTally;
import com.promptstudio.attempt.domain.CodeRunSummary;
import com.promptstudio.attempt.domain.TurnTestResults;
import com.promptstudio.attempt.repository.CodeRunRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 어템프트에 이미 쌓여 있는 실행 기록에서 턴별 채점 결과를 읽어 온다. 저장소는 건드리지 않는다 —
 * 값은 {@code code_run}·{@code code_run_case}에 다 있고, 그것을 피드백이 읽을 모양으로 접기만 한다.
 *
 * <p>턴마다 실행이 여러 번일 수 있어(사용자가 같은 턴에서 다시 돌린다) 턴별로 <b>가장 최근 종료 실행</b>
 * 하나만 고른다. 진행 중(QUEUED)인 실행은 결과가 없어 제외한다.
 */
@Component
public class TurnTestResultLoader {

    private final CodeRunRepository codeRunRepository;

    public TurnTestResultLoader(CodeRunRepository codeRunRepository) {
        this.codeRunRepository = codeRunRepository;
    }

    /**
     * @param turnCount 어템프트의 턴 수. 범위를 벗어난 turn_ordinal 행은 버린다
     */
    public TurnTestResults load(Long attemptId, int turnCount) {
        List<CodeRunSummary> runs = codeRunRepository.findAllByAttemptId(attemptId);

        if (runs.isEmpty()) {
            return TurnTestResults.EMPTY;
        }

        Map<Integer, CodeRunSummary> byTurn = new HashMap<>();
        CodeRunSummary baseline = null;

        // 최근순으로 오므로 처음 만난 것이 그 자리의 가장 최근 실행이다.
        for (CodeRunSummary run : runs) {
            if (!run.status().isTerminal()) {
                continue;
            }

            // turn_ordinal이 null인 행은 턴이 없는 시점의 실행, 즉 시작 스켈레톤이다
            // (릴레이 게임이 베이스라인을 잴 때 남긴다).
            if (run.turnOrdinal() == null) {
                if (baseline == null) {
                    baseline = run;
                }

                continue;
            }

            if (run.turnOrdinal() < 0 || run.turnOrdinal() >= turnCount) {
                continue;
            }

            byTurn.putIfAbsent(run.turnOrdinal(), run);
        }

        if (byTurn.isEmpty() && baseline == null) {
            return TurnTestResults.EMPTY;
        }

        Map<UUID, CodeRunCaseTally> tallies = codeRunRepository.tallyCasesByRunIds(runIds(byTurn, baseline));
        List<TurnTestResults.Graded> graded = new ArrayList<>();

        for (int turnOrdinal = 0; turnOrdinal < turnCount; turnOrdinal++) {
            CodeRunSummary run = byTurn.get(turnOrdinal);

            if (run == null) {
                continue;
            }

            graded.add(new TurnTestResults.Graded(
                    turnOrdinal, run.status(), tallies.get(run.id()), failedTestNames(run.id())));
        }

        return TurnTestResults.of(graded, gradedBaseline(baseline, tallies));
    }

    private TurnTestResults.Graded gradedBaseline(CodeRunSummary baseline, Map<UUID, CodeRunCaseTally> tallies) {
        if (baseline == null) {
            return null;
        }

        // 베이스라인의 실패 이름은 싣지 않는다 — 사용자가 아직 아무것도 고치지 않은 상태의 실패다.
        return new TurnTestResults.Graded(null, baseline.status(), tallies.get(baseline.id()), List.of());
    }

    /**
     * 실패한 테스트 이름. FAILED와 ERROR를 함께 센다 — 사용자가 봐야 할 곳은 다르지만 둘 다
     * "이 요구사항이 아직 안 됐다"는 같은 신호다. message는 싣지 않는다.
     */
    private List<String> failedTestNames(UUID runId) {
        List<String> names = new ArrayList<>();

        for (CodeRunCase testCase : codeRunRepository.findCasesByRunId(runId)) {
            if (testCase.status() == CodeRunCaseStatus.FAILED || testCase.status() == CodeRunCaseStatus.ERROR) {
                names.add(testCase.name());
            }
        }

        return names;
    }

    private List<UUID> runIds(Map<Integer, CodeRunSummary> byTurn, CodeRunSummary baseline) {
        List<UUID> ids = new ArrayList<>();

        for (CodeRunSummary run : byTurn.values()) {
            ids.add(run.id());
        }

        if (baseline != null) {
            ids.add(baseline.id());
        }

        return ids;
    }
}
