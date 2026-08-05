package com.promptstudio.relay.service;

import java.time.Instant;

/**
 * 턴 이력 한 줄. 스코어보드 복원용 — 놓친 grading.finished 브로드캐스트를 대신한다.
 *
 * @param passedCount null이면 "0개 통과"가 아니라 "채점 없음"이다
 * @param delta       직전 통과 수 대비 증가분. 기준이 없으면 null
 * @param skipped     치지 않고 건너뛴 턴(이탈·입력 마감 초과). 채점 없음(passedCount null)과
 *                    구분해야 한다 — 전자는 주자가 안 쳤고, 후자는 쳤는데 채점이 실패했다
 */
public record RelayTurnRecord(
        int turnIndex,
        int seatOrder,
        int lap,
        Long authorUserId,
        Integer passedCount,
        Integer totalCount,
        Integer delta,
        boolean skipped,
        Instant startedAt,
        Instant finishedAt
) {
}
