package com.promptstudio.guest;

import com.promptstudio.attempt.repository.IdempotencyRepository;
import com.promptstudio.guest.domain.GuestSession;
import com.promptstudio.guest.repository.GuestAttemptOwnershipRepository;
import com.promptstudio.guest.repository.GuestSessionCleanupRepository;
import com.promptstudio.guest.repository.GuestSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class GuestSessionService {

    private static final Logger log = LoggerFactory.getLogger(GuestSessionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GuestSessionRepository guestSessionRepository;
    private final GuestAttemptOwnershipRepository guestAttemptOwnershipRepository;
    private final GuestSessionCleanupRepository guestSessionCleanupRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final GuestSessionProperties properties;

    public GuestSessionService(
            GuestSessionRepository guestSessionRepository,
            GuestAttemptOwnershipRepository guestAttemptOwnershipRepository,
            GuestSessionCleanupRepository guestSessionCleanupRepository,
            IdempotencyRepository idempotencyRepository,
            GuestSessionProperties properties
    ) {
        this.guestSessionRepository = guestSessionRepository;
        this.guestAttemptOwnershipRepository = guestAttemptOwnershipRepository;
        this.guestSessionCleanupRepository = guestSessionCleanupRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.properties = properties;
    }

    /** 유효한 기존 게스트를 반환하거나 새 게스트를 만들고 HttpOnly 쿠키를 발급한다. */
    @Transactional
    public GuestSession resolveOrCreate(HttpServletRequest request, HttpServletResponse response) {
        Optional<GuestSession> existing = findValid(request);
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = Instant.now();
        String token = newToken();
        GuestSession created = guestSessionRepository.save(
                GuestSession.issue(hash(token), now, now.plus(properties.getTtl())));
        addCookie(response, token, properties.getTtl().toSeconds());

        log.info("Guest session issued | guestSessionId={} expiresAt={}", created.id(), created.expiresAt());
        return created;
    }

    /** 로그인 성공 시에만 사용한다. 유효한 게스트 세션이 없으면 아무 것도 이전하지 않는다. */
    @Transactional
    public int transferToUser(HttpServletRequest request, HttpServletResponse response, Long userId) {
        Optional<GuestSession> guestSession = findValid(request);
        if (guestSession.isEmpty()) {
            return 0;
        }

        UUID guestSessionId = guestSession.get().id();
        int movedAttempts = guestAttemptOwnershipRepository.transferToUser(guestSessionId, userId);
        idempotencyRepository.deleteByGuestSessionId(guestSessionId);
        guestSessionRepository.delete(guestSession.get());
        clearCookie(response);

        log.info("Guest attempts transferred after login | guestSessionId={} userId={} attempts={}",
                guestSessionId, userId, movedAttempts);
        return movedAttempts;
    }

    @Scheduled(fixedDelayString = "${GUEST_SESSION_CLEANUP_INTERVAL_MS:3600000}")
    @Transactional
    public void deleteExpiredSessions() {
        int deleted = guestSessionCleanupRepository.deleteExpiredOrphanSessions(Instant.now());
        if (deleted > 0) {
            log.info("Expired guest sessions deleted | count={}", deleted);
        }
    }

    private Optional<GuestSession> findValid(HttpServletRequest request) {
        String token = findCookieValue(request);
        if (token == null) {
            return Optional.empty();
        }

        // 여기서 "새로 발급한다"고 적지 않는다 — 이 메서드는 읽기만 하고, 발급 여부는 부르는 쪽이 정한다.
        // 랭킹처럼 조회만 하는 경로도 이 메서드를 지나가므로, 발급을 단정하면 로그가 거짓이 된다.
        return guestSessionRepository.findByTokenHashAndExpiresAtAfter(hash(token), Instant.now());
    }

    private String findCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (properties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), token)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite("Lax")
                .path("/api")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response) {
        addCookie(response, "", 0);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                encoded.append(String.format("%02x", value));
            }
            return encoded.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
