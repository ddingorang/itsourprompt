package com.promptstudio.problem.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "AI 코드 실행 요청")
public record RunRequest(
        @NotBlank(message = "prompt는 비어 있을 수 없습니다.")
        @Schema(description = "AI에게 전달할 작업 요청", example = "Hello, World!를 출력하도록 코드를 완성해줘")
        String prompt
) {
}
