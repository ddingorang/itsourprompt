package com.promptstudio.auth.controller.response;

import com.promptstudio.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 로그인 사용자 정보 응답. 비밀번호 해시는 절대 포함하지 않는다.
 * signup(201)·login(200)·GET /api/me(200)가 모두 이 형태를 반환한다.
 */
@Schema(description = "로그인 사용자 정보")
public record MeResponse(
        @Schema(description = "사용자 ID", example = "1")
        Long id,

        @Schema(description = "아이디", example = "prompter01")
        String username,

        @Schema(description = "닉네임", example = "프롬프터")
        String nickname,

        @Schema(description = "이메일", example = "prompter@example.com")
        String email,

        @Schema(description = "가입 시각(ISO-8601)", example = "2026-07-29T05:00:00Z")
        Instant createdAt
) {

    public static MeResponse from(User user) {
        return new MeResponse(user.id(), user.username(), user.nickname(), user.email(), user.createdAt());
    }
}
