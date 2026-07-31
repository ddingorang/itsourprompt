package com.promptstudio.guest.repository;

import com.promptstudio.guest.domain.GuestSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GuestSessionRepository extends JpaRepository<GuestSession, UUID> {

    Optional<GuestSession> findByTokenHashAndExpiresAtAfter(String tokenHash, java.time.Instant now);
}
