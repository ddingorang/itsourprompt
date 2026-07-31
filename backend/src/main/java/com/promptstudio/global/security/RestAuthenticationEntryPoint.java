package com.promptstudio.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증되지 않은 사용자가 보호 리소스에 접근했을 때 401을 JSON으로 반환한다.
 *
 * <p>Spring Security 기본 동작(로그인 페이지 리다이렉트/빈 응답)은 SPA에 맞지 않으므로,
 * 이 프로젝트의 공통 에러 포맷({@code {code, message}} — ApiErrorResponse와 동일)으로 직접 응답한다.
 * 고정 문자열이라 JSON 직렬화 라이브러리 의존 없이 문자열로 작성한다.</p>
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"unauthenticated\",\"message\":\"로그인이 필요합니다.\"}");
    }
}
