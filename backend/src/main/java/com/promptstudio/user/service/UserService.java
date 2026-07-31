package com.promptstudio.user.service;

import com.promptstudio.user.domain.User;
import com.promptstudio.user.exception.DuplicateEmailException;
import com.promptstudio.user.exception.DuplicateUsernameException;
import com.promptstudio.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 회원가입. 아이디/이메일 중복을 검사한 뒤 비밀번호를 BCrypt로 해시해 저장한다.
     *
     * <p>서비스 계층 중복 검사와 DB UNIQUE 제약이 이중 방어를 이룬다 —
     * 동시 가입 레이스로 UNIQUE 제약에 걸리면 DataIntegrityViolationException이
     * 발생하고 GlobalExceptionHandler가 409로 변환한다.</p>
     */
    @Transactional
    public User register(String username, String rawPassword, String nickname, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        return userRepository.save(User.create(username, passwordHash, nickname, email));
    }
}
