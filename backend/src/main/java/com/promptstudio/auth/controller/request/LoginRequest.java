package com.promptstudio.auth.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public record LoginRequest(
        @NotBlank(message = "username은 비어 있을 수 없습니다.")
        @Schema(description = "아이디", example = "prompter01")
        String username,

        @NotBlank(message = "password는 비어 있을 수 없습니다.")
        @Schema(description = "비밀번호", example = "password123!")
        String password
) {
}
