package com.promptstudio.attempt.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 어템프트의 턴별 채점 결과. 피드백 프롬프트 렌즈가 판정의 근거로 쓴다.
 *
 * <p>축 1 판정("요청한 대로 바뀌었어요")은 여기까지 추측이었다 — 파일에 변경이 보이면 요청이 반영된
 * 것으로 읽을 수밖에 없었다. 실행 결과가 들어오면 그 판정이 관찰이 된다.
 *
 * <p>집계와 델타 규칙은 릴레이가 이미 쓰던 것과 같다. 릴레이 쪽
 * {@code RelayGradeTally}·{@code RelayScoring}은 패키지 프라이빗이라 부를 수 없어 규칙만 복제했다 —
 * 한쪽을 고치면 다른 쪽도 같이 고쳐야 한다.
 *
 * @param baselinePassed 스켈레톤 원본 실행의 통과 수. 베이스라인 실행이 없거나 채점을 얻지 못했으면 null
 * @param baselineTotal  스켈레톤 원본 실행의 전체 케이스 수
 */
public record TurnTestResults(
        List<TurnTestResult> turns,
        Integer baselinePassed,
        Integer baselineTotal
) {

    /** 실행 기록이 하나도 없는 어템프트. 프롬프트에 채점 태그가 하나도 붙지 않는다. */
    public static final TurnTestResults EMPTY = new TurnTestResults(List.of(), null, null);

    public TurnTestResults {
        turns = List.copyOf(turns);
    }

    /**
     * 저장소가 고른 실행들을 델타까지 매겨 접는다.
     *
     * @param graded   turnOrdinal 오름차순. 종료된 실행이 없는 턴은 아예 빠져 있다
     * @param baseline 스켈레톤 원본 실행. 없으면 null
     */
    public static TurnTestResults of(List<Graded> graded, Graded baseline) {
        Integer baselinePassed = baseline == null ? null : passedOf(baseline);
        Integer baselineTotal = baseline == null ? null : totalOf(baseline);
        List<TurnTestResult> turns = new ArrayList<>();

        for (Graded turn : graded) {
            Integer passed = passedOf(turn);
            Base base = baseFor(turns, baselinePassed);

            turns.add(new TurnTestResult(
                    turn.turnOrdinal(),
                    turn.status(),
                    passed,
                    totalOf(turn),
                    delta(passed, base.passed()),
                    base.passed() == null ? null : base.turnOrdinal(),
                    List.copyOf(turn.failedTestNames())
            ));
        }

        return new TurnTestResults(turns, baselinePassed, baselineTotal);
    }

    public boolean isEmpty() {
        return turns.isEmpty() && baselinePassed == null;
    }

    /**
     * 이 턴의 채점 결과. 없으면 null이며, 그것은 통과 0건이 아니라 실행이 없다는 뜻이다.
     */
    public TurnTestResult forTurn(int turnOrdinal) {
        for (TurnTestResult turn : turns) {
            if (turn.turnOrdinal() == turnOrdinal) {
                return turn;
            }
        }

        return null;
    }

    /**
     * 케이스가 있으면 PASSED를 센다. 케이스가 없는 종료 상태(컴파일 실패·타임아웃 등)는 코드가 테스트에
     * 못 미친 것이므로 0개 통과다 — 앞 턴이 통과시킨 테스트를 깨뜨린 사실이 델타에 음수로 드러나야 한다.
     *
     * <p>단 RUNNER_ERROR는 코드가 아니라 채점 인프라의 실패라 통과 수를 알 수 없으므로 null로 남긴다.
     * null을 0으로 뭉개면 인프라 사고가 사용자의 감점으로 둔갑한다.
     */
    private static Integer passedOf(Graded graded) {
        if (graded.status() == CodeRunStatus.RUNNER_ERROR) {
            return null;
        }

        return graded.tally() == null ? 0 : graded.tally().passed();
    }

    private static Integer totalOf(Graded graded) {
        if (graded.status() == CodeRunStatus.RUNNER_ERROR) {
            return null;
        }

        return graded.tally() == null ? 0 : graded.tally().total();
    }

    /**
     * 이 턴의 비교 기준. 채점을 얻지 못한 턴(passed null)은 건너뛰고 가장 가까운 이전 턴과 비교하며,
     * 그런 턴이 없으면 베이스라인이 기준이다. 기준을 못 찾으면 델타도 없다.
     */
    private static Base baseFor(List<TurnTestResult> settled, Integer baselinePassed) {
        for (int index = settled.size() - 1; index >= 0; index--) {
            TurnTestResult previous = settled.get(index);

            if (previous.passed() != null) {
                return new Base(previous.passed(), previous.turnOrdinal());
            }
        }

        return new Base(baselinePassed, null);
    }

    /** 음수를 깎지 않는다 — 앞 턴의 통과를 깨뜨린 것도 그대로 드러나야 한다. */
    private static Integer delta(Integer passed, Integer previousPassed) {
        if (passed == null || previousPassed == null) {
            return null;
        }

        return passed - previousPassed;
    }

    /**
     * 저장소가 고른 실행 한 건의 재료. 상태와 집계, 실패한 테스트 이름만 담는다.
     *
     * @param turnOrdinal 0-based 턴 번호. 베이스라인이면 null
     * @param tally       케이스 집계. 케이스 기록이 없으면 null이며 그것은 통과 0건이다
     */
    public record Graded(
            Integer turnOrdinal,
            CodeRunStatus status,
            CodeRunCaseTally tally,
            List<String> failedTestNames
    ) {
    }

    /**
     * 턴 하나의 채점 결과.
     *
     * @param passed          통과 수. RUNNER_ERROR면 null이고 그것은 0이 아니라 알 수 없음이다
     * @param delta           직전 기준 대비 통과 수 변화. 기준을 못 찾으면 null
     * @param deltaBaseOrdinal 델타의 기준이 된 턴. null이면 베이스라인이 기준이다(delta가 null이면 의미 없음)
     * @param failedTestNames FAILED·ERROR로 끝난 테스트 이름
     */
    public record TurnTestResult(
            int turnOrdinal,
            CodeRunStatus status,
            Integer passed,
            Integer total,
            Integer delta,
            Integer deltaBaseOrdinal,
            List<String> failedTestNames
    ) {
    }

    /** 델타 기준 한 자리. ordinal이 null이면 베이스라인이다. */
    private record Base(Integer passed, Integer turnOrdinal) {
    }
}
