package com.promptstudio.global.config;

import com.promptstudio.global.security.RestAccessDeniedHandler;
import com.promptstudio.global.security.RestAuthenticationEntryPoint;
import com.promptstudio.guest.GuestSessionFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * 세션 + HttpOnly 쿠키 기반 인증 설정.
 *
 * <p><b>인가 정책</b>: 보호가 필요한 경로({@code /api/me/**})만 인증을 요구하고
 * 나머지는 전부 공개한다({@code anyRequest().permitAll()}).
 * 이번 로그인 도입 범위에서는 기존 문제/어템프트 API를 전부 비로그인 허용으로 유지해야 하고,
 * 공개 경로를 열거하는 방식은 /error·springdoc 경로 등을 빠뜨려 예기치 않은 401을 만들 수 있어
 * "잠글 것만 잠그는" 방식을 택했다. 어템프트에 소유자 개념이 생기면 이 정책을 다시 조인다.</p>
 *
 * <p><b>CSRF는 의도적으로 비활성화</b>: 현재 인증이 필요한 상태 변경 API가 없고
 * (로그인 세션으로 보호되는 쓰기 엔드포인트가 아직 없음), CSRF를 켜면 기존의 모든
 * 비인증 POST(어템프트 생성/턴/제출)와 해당 MockMvc 테스트, 프론트 공용 apiClient까지
 * 전부 토큰 처리를 추가해야 한다. 어템프트 소유자 도입 시점에 CookieCsrfTokenRepository로
 * 활성화한다(적용 방법은 login-sandbox에서 검증 완료).</p>
 *
 * <p><b>세션 저장소</b>: 톰캣 기본 인메모리 세션. 단일 인스턴스 배포 전제
 * (FeedbackGenerationGuard와 동일한 전제)이며, 다중 인스턴스가 필요해지면
 * spring-session-data-redis로 전환한다.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 비밀번호는 항상 BCrypt 해시로 저장·대조한다(평문 저장 금지). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 로그인 시 아이디/비밀번호 검증에 사용한다(AuthController가 직접 호출). */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 로그인 성공 시 인증 정보를 HTTP 세션에 저장하기 위한 저장소.
     * AuthController가 saveContext()를 호출해야 세션 쿠키(JSESSIONID)로 로그인이 유지된다.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            GuestSessionFilter guestSessionFilter
    ) throws Exception {
        http
                // 기존 컨트롤러의 @CrossOrigin 설정을 인식해 preflight(OPTIONS)를 인증 앞단에서 처리한다.
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/me/**").authenticated()
                        .requestMatchers("/api/attempts/**").permitAll()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // SPA용 JSON 로그인(AuthController)을 직접 구현하므로 기본 로그인 방식은 모두 끈다.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        // 인증 컨텍스트를 읽은 직후 게스트를 해석해야 로그인 요청에는 새 게스트가 만들어지지 않는다.
        http.addFilterAfter(guestSessionFilter, SecurityContextHolderFilter.class);

        return http.build();
    }
}
