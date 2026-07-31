package com.promptstudio.me.controller.response;

import com.promptstudio.me.domain.SubmittedAttempt;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "현재 사용자가 제출 완료한 풀이 한 건")
public record SubmittedAttemptResponse(
        @Schema(description = "피드백 조회에 사용하는 어템프트 ID", example = "42")
        Long attemptId,

        @Schema(description = "문제 ID", example = "3")
        Long problemId,

        @Schema(description = "문제 제목", example = "사용자 프로필 컴포넌트")
        String problemTitle,

        @Schema(description = "제출 완료 시각(ISO-8601). 기존 기록은 null일 수 있음", example = "2026-07-31T05:10:32Z")
        Instant submittedAt
) {

    public static SubmittedAttemptResponse from(SubmittedAttempt attempt) {
        return new SubmittedAttemptResponse(
                attempt.attemptId(), attempt.problemId(), attempt.problemTitle(), attempt.submittedAt());
    }
}
