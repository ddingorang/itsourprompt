package com.promptstudio.me.controller;

import com.promptstudio.auth.controller.response.MeResponse;
import com.promptstudio.global.exception.ApiErrorResponse;
import com.promptstudio.global.security.AppUserDetails;
import com.promptstudio.user.domain.User;
import com.promptstudio.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 로그인 사용자 전용 API. SecurityConfig에서 /api/me/** 는 인증 필수로 설정되어
 * 비로그인 요청은 컨트롤러에 도달하기 전에 401({code:"unauthenticated"})로 차단된다.
 *
 * <p>URL에 사용자 ID를 노출하지 않는 것("/api/users/{id}"가 아닌 "/api/me")이 의도된 설계다 —
 * "나"는 세션이 알고 있으므로, 남의 ID를 넣어 조회를 시도하는 것 자체가 불가능하다.</p>
 */
@RestController
@RequestMapping("/api/me")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(name = "Me", description = "현재 로그인 사용자 API")
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(
            summary = "내 정보 조회",
            description = "세션 쿠키로 식별된 현재 로그인 사용자의 정보를 반환합니다. "
                    + "SPA가 앱 시작 시 이 API를 호출해 로그인 여부를 확인하는 용도로도 사용됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MeResponse.class))),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public MeResponse me(@AuthenticationPrincipal AppUserDetails principal) {
        // 세션(principal)에는 최소 정보만 있으므로 닉네임·이메일·가입시각은 DB에서 다시 조회한다.
        // (세션에 전부 담으면 세션이 비대해지고, 닉네임 변경 등 이후 기능에서 신선도 문제가 생긴다.)
        User user = userRepository.findById(principal.id()).orElseThrow();
        return MeResponse.from(user);
    }
}
