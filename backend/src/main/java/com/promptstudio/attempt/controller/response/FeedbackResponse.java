package com.promptstudio.attempt.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프롬프트 피드백 결과")
public record FeedbackResponse(
        @Schema(description = "어템프트 전체 턴 기록에 대한 프롬프트 피드백 (Markdown)")
        String feedback
) {
}
