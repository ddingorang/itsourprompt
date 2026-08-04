package com.promptstudio.relay.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "릴레이 턴 전송 요청")
public record RelayTurnRequest(
        @NotBlank(message = "prompt는 비어 있을 수 없습니다.")
        @Schema(description = "AI에게 전달할 작업 요청", example = "이전 코드의 출력 형식을 문제 명세에 맞게 고쳐줘")
        String prompt
) {
}
