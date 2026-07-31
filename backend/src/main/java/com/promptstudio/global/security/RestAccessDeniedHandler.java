package com.promptstudio.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증은 되었으나 권한이 부족한 요청에 403을 JSON으로 반환한다.
 *
 * <p>현재는 권한 등급이 하나(일반 사용자)뿐이라 실제로 도달할 일이 거의 없지만,
 * 관리자 권한 등이 도입될 때를 대비해 401(RestAuthenticationEntryPoint)과
 * 같은 포맷({@code {code, message}})으로 맞춰 둔다.</p>
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"access-denied\",\"message\":\"접근 권한이 없습니다.\"}");
    }
}
