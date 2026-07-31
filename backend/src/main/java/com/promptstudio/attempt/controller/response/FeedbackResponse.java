package com.promptstudio.attempt.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "프롬프트 피드백 결과")
public record FeedbackResponse(
        @Schema(description = "턴별 피드백. 턴별 피드백 이전에 제출된 어템프트는 빈 배열이다.")
        List<TurnFeedback> turns,

        @Schema(description = "세션 전체에 대한 피드백 (Markdown)")
        String overallMd
) {

    @Schema(description = "한 턴의 프롬프트 피드백")
    public record TurnFeedback(
            @Schema(description = "턴 번호. 1부터 시작한다.", example = "1")
            int turn,

            @Schema(description = "해당 턴 프롬프트에 대한 피드백 (Markdown)")
            String feedbackMd
    ) {
    }
}
