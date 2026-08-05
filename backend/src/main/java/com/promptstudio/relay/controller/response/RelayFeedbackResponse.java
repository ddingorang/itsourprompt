package com.promptstudio.relay.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "릴레이 게임 피드백. 턴별 피드백이 곧 주자별 피드백이다")
public record RelayFeedbackResponse(
        @Schema(description = "게임 전체에 대한 총평")
        String overall,
        @Schema(description = "턴별(주자별) 피드백. 진행 인덱스 오름차순")
        List<RelayTurnFeedbackResponse> turns
) {

    @Schema(description = "턴 하나의 피드백과 점수")
    public record RelayTurnFeedbackResponse(
            @Schema(description = "릴레이 진행 인덱스(0-based)", example = "3")
            int turnIndex,
            @Schema(description = "좌석(0-based)", example = "1")
            int seatOrder,
            @Schema(description = "바퀴(0-based)", example = "1")
            int lap,
            @Schema(description = "이 턴을 친 사용자 ID", example = "7")
            Long authorUserId,
            @Schema(description = "표시 이름", example = "프롬프터")
            String nickname,
            @Schema(description = "이 턴의 프롬프트에 대한 AI 피드백")
            String feedback,
            @Schema(description = "통과한 테스트 수. null이면 채점 없음", example = "3")
            Integer passedCount,
            @Schema(description = "전체 테스트 수", example = "5")
            Integer totalCount,
            @Schema(description = "직전 대비 증가분 = 기여도. 기준이 없으면 null", example = "1")
            Integer delta
    ) {
    }
}
