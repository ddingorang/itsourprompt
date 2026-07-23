package com.promptstudio.problem.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프롬프트 피드백 응답")
public record SubmitResponse(
        @Schema(description = "제출한 프롬프트에 대한 Markdown 형식 피드백")
        String feedback
) {
}
