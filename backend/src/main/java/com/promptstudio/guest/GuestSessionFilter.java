package com.promptstudio.guest;

import com.promptstudio.guest.domain.GuestSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 프론트가 앱 시작 시 호출하는 /api/me와 모든 attempt API에서 게스트 세션을 해석한다.
 * 쿠키 원문은 요청 속성에 보관하지 않고, DB 식별자만 이후 권한 검사에 전달한다.
 */
@Component
public class GuestSessionFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE = GuestSessionFilter.class.getName() + ".guestSessionId";

    private final GuestSessionService guestSessionService;

    public GuestSessionFilter(GuestSessionService guestSessionService) {
        this.guestSessionService = guestSessionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("/api/me".equals(path) || path.startsWith("/api/attempts"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isLoggedIn()) {
            GuestSession guestSession = guestSessionService.resolveOrCreate(request, response);
            request.setAttribute(REQUEST_ATTRIBUTE, guestSession.id());
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLoggedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
