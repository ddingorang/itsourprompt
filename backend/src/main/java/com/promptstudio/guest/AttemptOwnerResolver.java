package com.promptstudio.guest;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.global.security.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AttemptOwnerResolver {

    private final GuestSessionService guestSessionService;

    public AttemptOwnerResolver(GuestSessionService guestSessionService) {
        this.guestSessionService = guestSessionService;
    }

    public AttemptOwner resolve(AppUserDetails principal, HttpServletRequest request) {
        if (principal != null) {
            return AttemptOwner.user(principal.id());
        }

        Object value = request.getAttribute(GuestSessionFilter.REQUEST_ATTRIBUTE);
        if (value instanceof UUID guestSessionId) {
            return AttemptOwner.guest(guestSessionId);
        }

        throw new IllegalStateException("Guest session was not resolved for an attempt request");
    }

    /**
     * 요청이 이미 들고 온 소유자만 해석하고, 없으면 빈 값을 돌려준다. {@link #resolve}와 달리
     * 던지지 않는 것이 요점이다 — 랭킹처럼 누구나 볼 수 있는 조회는 소유자를 몰라도 성립한다.
     *
     * <p>GuestSessionFilter가 걸리지 않는 경로에서도 쓰이므로 요청 속성이 아니라 쿠키를 직접 본다.
     * 세션을 새로 만들지는 않는다.
     */
    public Optional<AttemptOwner> resolveExisting(AppUserDetails principal, HttpServletRequest request) {
        if (principal != null) {
            return Optional.of(AttemptOwner.user(principal.id()));
        }

        return guestSessionService.findExistingSessionId(request).map(AttemptOwner::guest);
    }
}
