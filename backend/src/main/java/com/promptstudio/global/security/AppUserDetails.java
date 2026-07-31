package com.promptstudio.global.security;

import com.promptstudio.user.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 세션에 저장되는 인증 주체(principal).
 *
 * <p>컨트롤러에서 {@code @AuthenticationPrincipal AppUserDetails}로 받아
 * {@link #id()}로 현재 사용자 ID를 얻는다. <b>사용자 식별자는 절대 클라이언트
 * 요청 값으로 받지 않고 항상 여기서 꺼낸다</b>(위조 방지의 핵심).</p>
 *
 * <p>세션 비대와 데이터 신선도 문제를 피하려고 최소 정보(id, username, 비밀번호 해시)만
 * 담는다. 닉네임·이메일 등 나머지는 필요한 시점에 DB에서 다시 조회한다(MeController 참조).</p>
 */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;

    public AppUserDetails(User user) {
        this.id = user.id();
        this.username = user.username();
        this.passwordHash = user.passwordHash();
    }

    public Long id() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
