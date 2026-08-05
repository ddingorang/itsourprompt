package com.promptstudio.global.config;

import com.promptstudio.global.logging.RequestLogFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 허용 오리진을 한곳에서 설정으로 관리한다.
 *
 * <p>이전에는 컨트롤러마다 {@code @CrossOrigin(origins = "http://localhost:5173")}을 달아
 * 개발 서버 주소가 코드에 박혀 있었다. 그래서 배포 도메인에서 오는 요청이 전부
 * 403 {@code Invalid CORS request}로 막혔다 — 로컬은 Vite 프록시가 브라우저의
 * {@code Origin: http://localhost:5173}을 그대로 전달해 우연히 통과했을 뿐이다.</p>
 *
 * <p>주의할 점은 이 검사가 preflight에만 걸리는 게 아니라는 것이다. 브라우저는 GET을 제외한
 * same-origin 요청에도 Origin 헤더를 붙이므로, nginx가 같은 오리진에서 /api를 프록시하는
 * 배포 환경에서도 서버가 그 Origin을 모르면 preflight 없이 곧바로 403이 난다.</p>
 *
 * <p>{@code SecurityConfig}의 {@code .cors(Customizer.withDefaults())}는 별도 빈이 없으면
 * {@code HandlerMappingIntrospector}를 통해 여기 등록한 MVC 설정을 그대로 재사용한다.
 * 따라서 시큐리티 필터와 MVC 핸들러 매핑이 같은 허용 목록을 본다.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 이 줄이 없으면 브라우저 JS가 X-Request-Id를 읽지 못해 사용자가 문의할 때 추적 id를 말할 수 없다.
                .exposedHeaders(RequestLogFilter.REQUEST_ID_HEADER)
                // 세션 쿠키(JSESSIONID)를 주고받아야 하므로 필요하다.
                // 이 값이 true면 와일드카드 오리진을 쓸 수 없어 오리진을 정확히 열거한다.
                .allowCredentials(true);
    }
}
