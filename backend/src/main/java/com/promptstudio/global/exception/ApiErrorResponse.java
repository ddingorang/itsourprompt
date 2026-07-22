package com.promptstudio.global.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "API 오류 응답")
public record ApiErrorResponse(
        @Schema(description = "오류 코드", example = "problem-not-found")
        String code,
        @Schema(description = "오류 메시지", example = "문제 ID 999를 찾을 수 없습니다.")
        String message
) {
}
