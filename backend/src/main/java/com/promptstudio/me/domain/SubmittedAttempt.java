package com.promptstudio.me.domain;

import java.time.Instant;

/** 현재 사용자가 제출 완료한 풀이 한 건의 목록 조회용 읽기 모델. */
public record SubmittedAttempt(
        Long attemptId,
        Long problemId,
        String problemTitle,
        Instant submittedAt
) {
}
