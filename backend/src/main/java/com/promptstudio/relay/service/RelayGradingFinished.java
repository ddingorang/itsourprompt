package com.promptstudio.relay.service;

import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.relay.domain.RelayRoomView;

import java.util.List;

/**
 * 이번 턴의 채점이 끝나 좌석이 전진했다. 브로드캐스터가 grading.finished와 room.state를
 * 이 순서로 내보낸다.
 */
public record RelayGradingFinished(RelayRoomView room, Outcome outcome) {

    /**
     * @param delta   직전 통과 수 대비 증가분 = 이 주자의 기여도. 음수일 수 있다 — 앞사람이
     *                통과시킨 테스트를 깨뜨린 경우이고, 그게 드러나는 것이 이 게임의 재미다.
     *                기준(직전 값·베이스라인)이 없으면 null
     * @param skipped 채점 결과를 얻지 못하고 전진했다. passed/total/delta는 null이다
     */
    public record Outcome(
            int turnIndex,
            Long authorUserId,
            CodeRunStatus runStatus,
            Integer passed,
            Integer total,
            Integer delta,
            List<FailedCase> failedCases,
            boolean skipped
    ) {

        public static Outcome graded(int turnIndex, Long authorUserId, RelayGradeTally tally, Integer delta) {
            return new Outcome(
                    turnIndex,
                    authorUserId,
                    tally.runStatus(),
                    tally.passed(),
                    tally.total(),
                    delta,
                    tally.failedCases(),
                    false
            );
        }

        public static Outcome skipped(int turnIndex, Long authorUserId) {
            return new Outcome(turnIndex, authorUserId, null, null, null, null, List.of(), true);
        }
    }

    /** 실패한 케이스 하나. 이름이 한글 요구사항 문장이라 그대로가 훈수의 재료다. */
    public record FailedCase(String name, String message) {
    }
}
