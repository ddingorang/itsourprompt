package com.promptstudio.global.config;

import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 허용 오리진이 설정값으로 열린다는 것을 확인한다.
 *
 * <p>여기서 검증하는 seam은 "브라우저가 붙인 Origin 헤더를 서버가 통과시키는가"다.
 * 배포 도메인을 그대로 쓰면 도메인이 바뀔 때마다 테스트가 깨지므로, 테스트는 자기만의
 * 오리진을 프로퍼티로 주입해 메커니즘만 검증하고 실제 배포 도메인은 application.yml이 갖는다.</p>
 *
 * <p>실제 요청(preflight가 아닌 GET/POST)까지 검증하는 이유: 배포 환경은 nginx가 같은
 * 오리진에서 /api를 프록시하므로 preflight가 아예 발생하지 않는데, 브라우저는 same-origin
 * 요청에도 Origin 헤더를 붙인다. 서버가 그 Origin을 모르면 preflight 없이 곧바로
 * 403 "Invalid CORS request"가 난다 — 배포에서 실제로 터진 경로가 이쪽이다.</p>
 */
@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173,https://front.example.com")
class CorsApiTest extends DatabaseTest {

    private static final String ALLOWED_ORIGIN = "https://front.example.com";
    private static final String DENIED_ORIGIN = "https://intruder.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 허용된_오리진의_preflight를_통과시킨다() throws Exception {
        mockMvc.perform(options("/api/attempts")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                // 노출하지 않으면 브라우저 JS가 추적 id를 읽지 못한다.
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-Id"));
    }

    @Test
    void 허용된_오리진의_실제_요청을_통과시킨다() throws Exception {
        mockMvc.perform(get("/api/problems").header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk());
    }

    @Test
    void 허용_목록에_없는_오리진은_거부한다() throws Exception {
        mockMvc.perform(options("/api/attempts")
                        .header("Origin", DENIED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 개발용_오리진은_계속_통과시킨다() throws Exception {
        mockMvc.perform(get("/api/problems").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk());
    }
}
