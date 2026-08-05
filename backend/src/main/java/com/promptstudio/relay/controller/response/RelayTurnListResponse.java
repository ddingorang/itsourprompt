package com.promptstudio.relay.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "릴레이 턴 이력. 재접속한 참가자가 스코어보드를 복원하는 데 쓴다")
public record RelayTurnListResponse(
        @Schema(description = "완료된 턴들. 진행 인덱스 오름차순")
        List<RelayTurnRecordResponse> turns
) {

    @Schema(description = "턴 이력 한 줄")
    public record RelayTurnRecordResponse(
            @Schema(description = "릴레이 진행 인덱스(0-based)", example = "3")
            int turnIndex,
            @Schema(description = "좌석(0-based)", example = "1")
            int seatOrder,
            @Schema(description = "바퀴(0-based)", example = "1")
            int lap,
            @Schema(description = "이 턴을 친 사용자 ID", example = "7")
            Long authorUserId,
            @Schema(description = "통과한 테스트 수. null이면 채점 없음", example = "3")
            Integer passedCount,
            @Schema(description = "전체 테스트 수", example = "5")
            Integer totalCount,
            @Schema(description = "직전 대비 증가분 = 기여도. 기준이 없으면 null", example = "1")
            Integer delta,
            @Schema(
                    description = "치지 않고 건너뛴 턴인지(이탈·입력 마감 초과). true면 이 턴은 "
                            + "코드 진화에 흔적이 없다",
                    example = "false"
            )
            boolean skipped,
            @Schema(description = "턴 시작(프롬프트 접수) 시각")
            Instant startedAt,
            @Schema(description = "코드 생성이 끝난 시각")
            Instant finishedAt
    ) {
    }
}
