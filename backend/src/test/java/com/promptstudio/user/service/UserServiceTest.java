package com.promptstudio.user.service;

import com.promptstudio.support.DatabaseTest;
import com.promptstudio.user.domain.User;
import com.promptstudio.user.exception.DuplicateEmailException;
import com.promptstudio.user.exception.DuplicateUsernameException;
import com.promptstudio.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest extends DatabaseTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 비밀번호가_BCrypt_해시로_저장된다() {
        User saved = userService.register("alice", "password123", "앨리스", "alice@example.com");

        User found = userRepository.findById(saved.id()).orElseThrow();
        assertThat(found.passwordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", found.passwordHash())).isTrue();
    }

    @Test
    void 가입하면_아이디_닉네임_이메일_가입시각이_저장된다() {
        User saved = userService.register("alice", "password123", "앨리스", "alice@example.com");

        User found = userRepository.findByUsername("alice").orElseThrow();
        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.nickname()).isEqualTo("앨리스");
        assertThat(found.email()).isEqualTo("alice@example.com");
        assertThat(found.createdAt()).isNotNull();
    }

    @Test
    void 중복_아이디면_예외가_발생한다() {
        userService.register("alice", "password123", "앨리스", "alice@example.com");

        assertThatThrownBy(() -> userService.register("alice", "password456", "다른앨리스", "other@example.com"))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void 중복_이메일이면_예외가_발생한다() {
        userService.register("alice", "password123", "앨리스", "alice@example.com");

        assertThatThrownBy(() -> userService.register("bob", "password456", "밥", "alice@example.com"))
                .isInstanceOf(DuplicateEmailException.class);
    }
}
