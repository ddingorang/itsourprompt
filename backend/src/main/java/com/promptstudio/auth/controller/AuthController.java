package com.promptstudio.auth.controller;

import com.promptstudio.auth.controller.request.LoginRequest;
import com.promptstudio.auth.controller.request.SignupRequest;
import com.promptstudio.auth.controller.response.MeResponse;
import com.promptstudio.global.exception.ApiErrorResponse;
import com.promptstudio.global.security.AppUserDetails;
import com.promptstudio.user.domain.User;
import com.promptstudio.user.repository.UserRepository;
import com.promptstudio.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 + HttpOnly 쿠키 기반 인증 API.
 *
 * <p>로그인 성공 시 인증 정보를 HTTP 세션에 저장하고, 응답에 JSESSIONID 쿠키(HttpOnly)가
 * 실린다. 이후 브라우저가 쿠키를 자동 전송하므로 클라이언트는 토큰을 저장할 필요가 없다.</p>
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(name = "Auth", description = "회원가입 / 로그인 / 로그아웃 API")
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            UserService userService,
            UserRepository userRepository,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository
    ) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "회원가입",
            description = "아이디·비밀번호·닉네임·이메일로 계정을 생성합니다. 비밀번호는 BCrypt 해시로 저장됩니다. "
                    + "가입만으로는 로그인되지 않으며(세션 미생성), 클라이언트가 이어서 로그인 API를 호출해야 합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "가입 성공",
                    content = @Content(schema = @Schema(implementation = MeResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 아이디 또는 이메일",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public MeResponse signup(@Valid @RequestBody SignupRequest request) {
        User user = userService.register(
                request.username(),
                request.password(),
                request.nickname(),
                request.email()
        );
        return MeResponse.from(user);
    }

    @PostMapping("/login")
    @Operation(
            summary = "로그인",
            description = "아이디/비밀번호를 검증하고 세션을 생성합니다. 성공 시 HttpOnly 세션 쿠키(JSESSIONID)가 발급되며, "
                    + "응답 본문으로 사용자 정보를 반환하므로 추가로 /api/me를 호출할 필요가 없습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공(세션 쿠키 발급)",
                    content = @Content(schema = @Schema(implementation = MeResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public MeResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        // 1) 아이디/비밀번호 검증 — 실패 시 BadCredentialsException(→ 401 bad-credentials)
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );

        // 2) 인증 결과를 SecurityContext에 담고
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 3) 세션에 저장 — 이 호출이 있어야 응답에 세션 쿠키가 실리고 로그인이 유지된다.
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        // 닉네임·이메일·가입시각은 세션(principal)에 없으므로 DB에서 조회해 채운다.
        User user = userRepository.findById(principal.id()).orElseThrow();
        return MeResponse.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "로그아웃",
            description = "세션을 무효화합니다. 로그인 상태가 아니어도(세션이 이미 만료됐어도) 204를 반환하는 멱등 동작입니다. "
                    + "204 No Content로 응답하는 이유는 프론트 공용 apiClient가 204만 빈 본문으로 처리하기 때문입니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 완료(빈 응답)")
    })
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false); // false: 세션이 없으면 새로 만들지 않는다.
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
