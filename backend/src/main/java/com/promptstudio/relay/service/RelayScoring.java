package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayTurn;

import java.util.List;

/**
 * 릴레이 점수 규칙. 주자의 기여도 = 이번 턴 통과 수 - 직전 통과 수.
 *
 * <p>채점 전진(writer)과 이력 조회(피드백·턴 목록)가 같은 규칙으로 계산해야 하므로 한곳에 둔다.
 */
final class RelayScoring {

    private RelayScoring() {
    }

    /**
     * 이 턴의 직전 통과 수. 채점을 얻지 못한 턴(passed null)은 건너뛰고 가장 가까운 값과 비교하고,
     * 첫 턴이면 베이스라인이 기준이다. 기준을 못 찾으면 null — 델타도 null이 된다.
     *
     * @param turns turnIndex 오름차순 정렬 목록
     */
    static Integer previousPassed(List<RelayTurn> turns, int turnIndex, Integer baselinePassed) {
        for (int index = turns.size() - 1; index >= 0; index--) {
            RelayTurn turn = turns.get(index);

            if (turn.turnIndex() < turnIndex && turn.passedCount() != null) {
                return turn.passedCount();
            }
        }

        return baselinePassed;
    }

    /** 음수를 깎지 않는다 — 앞사람의 통과를 깨뜨린 것도 점수에 드러나야 한다. */
    static Integer delta(Integer passed, Integer previousPassed) {
        if (passed == null || previousPassed == null) {
            return null;
        }

        return passed - previousPassed;
    }
}
