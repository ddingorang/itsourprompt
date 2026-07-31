package com.promptstudio.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * 로그인 사용자 계정.
 *
 * <p>비밀번호는 반드시 BCrypt 해시로만 저장한다(필드명이 passwordHash인 이유).
 * 평문 비밀번호는 UserService.register()에서 해시된 직후 버려진다.</p>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    private User(String username, String passwordHash, String nickname, String email, Instant createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.email = email;
        this.createdAt = createdAt;
    }

    /** 회원가입 시점에 사용하는 팩토리. passwordHash는 이미 BCrypt로 해시된 값이어야 한다. */
    public static User create(String username, String passwordHash, String nickname, String email) {
        return new User(username, passwordHash, nickname, email, Instant.now());
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String nickname() {
        return nickname;
    }

    public String email() {
        return email;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }

        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
