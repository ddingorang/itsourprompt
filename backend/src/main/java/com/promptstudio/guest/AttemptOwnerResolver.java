package com.promptstudio.guest;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.global.security.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AttemptOwnerResolver {

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
}
