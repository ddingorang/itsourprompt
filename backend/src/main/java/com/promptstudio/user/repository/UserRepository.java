package com.promptstudio.user.repository;

import com.promptstudio.user.domain.User;

import java.util.Optional;

/**
 * user 도메인의 저장소 포트. 서비스는 이 인터페이스에만 의존한다.
 * (기존 ProblemRepository/AttemptRepository와 동일한 패턴)
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
