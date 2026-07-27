package com.promptstudio.attempt.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "어템프트 생성 요청")
public record CreateAttemptRequest(
        @NotNull(message = "problemId는 비어 있을 수 없습니다.")
        @Schema(description = "풀이를 시작할 문제 ID", example = "1")
        Long problemId
) {
}
