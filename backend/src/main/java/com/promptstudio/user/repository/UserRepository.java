package com.promptstudio.user.repository;

import com.promptstudio.user.domain.User;

import java.util.List;
import java.util.Optional;

/**
 * user 도메인의 저장소 포트. 서비스는 이 인터페이스에만 의존한다.
 * (기존 ProblemRepository/AttemptRepository와 동일한 패턴)
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    /**
     * 여러 사용자의 표시 이름을 한 번에 읽는 경로. 릴레이 방 참가자 목록처럼 매 변경마다
     * 전원을 렌더링해야 하는 화면이 id별 조회를 반복하지 않게 한다.
     */
    List<User> findAllById(Iterable<Long> ids);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
