package com.promptstudio.auth.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignupRequest(
        @NotBlank(message = "username은 비어 있을 수 없습니다.")
        @Size(min = 3, max = 30, message = "username은 3~30자여야 합니다.")
        @Schema(description = "로그인에 사용할 아이디", example = "prompter01")
        String username,

        @NotBlank(message = "password는 비어 있을 수 없습니다.")
        @Size(min = 8, max = 100, message = "password는 8~100자여야 합니다.")
        @Schema(description = "비밀번호(8자 이상). 서버에 BCrypt 해시로만 저장된다.", example = "password123!")
        String password,

        @NotBlank(message = "nickname은 비어 있을 수 없습니다.")
        @Size(min = 2, max = 30, message = "nickname은 2~30자여야 합니다.")
        @Schema(description = "화면에 표시할 닉네임", example = "프롬프터")
        String nickname,

        @NotBlank(message = "email은 비어 있을 수 없습니다.")
        @Email(message = "email 형식이 올바르지 않습니다.")
        @Size(max = 255)
        @Schema(description = "이메일", example = "prompter@example.com")
        String email
) {
}
