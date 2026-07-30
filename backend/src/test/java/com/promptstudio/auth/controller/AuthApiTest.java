package com.promptstudio.auth.controller;

import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세션 + 쿠키 인증 API 테스트. 로그인 후 요청은 MockMvc가 돌려준 세션(MockHttpSession)을
 * 재사용해 쿠키 기반 인증 유지를 재현한다.
 */
@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
class AuthApiTest extends DatabaseTest {

    private static final String SIGNUP_BODY = """
            {
              "username": "alice",
              "password": "password123",
              "nickname": "앨리스",
              "email": "alice@example.com"
            }
            """;

    private static final String LOGIN_BODY = """
            {
              "username": "alice",
              "password": "password123"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 회원가입하면_201과_사용자정보를_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.nickname").value("앨리스"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void 중복_아이디로_가입하면_409를_반환한다() throws Exception {
        signup();

        String sameUsername = """
                {
                  "username": "alice",
                  "password": "password456",
                  "nickname": "다른앨리스",
                  "email": "other@example.com"
                }
                """;
        mockMvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON).content(sameUsername))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-username"));
    }

    @Test
    void 중복_이메일로_가입하면_409를_반환한다() throws Exception {
        signup();

        String sameEmail = """
                {
                  "username": "bob",
                  "password": "password456",
                  "nickname": "바비",
                  "email": "alice@example.com"
                }
                """;
        mockMvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON).content(sameEmail))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-email"));
    }

    @Test
    void 형식이_잘못된_요청으로_가입하면_400을_반환한다() throws Exception {
        String shortPasswordAndBadEmail = """
                {
                  "username": "alice",
                  "password": "short",
                  "nickname": "앨리스",
                  "email": "not-an-email"
                }
                """;
        mockMvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON).content(shortPasswordAndBadEmail))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));
    }

    @Test
    void 로그인하면_세션에_인증정보가_저장된다() throws Exception {
        signup();

        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.nickname").value("앨리스"))
                .andReturn();

        // MockMvc는 실제 서블릿 컨테이너가 아니라 Set-Cookie(JSESSIONID) 헤더를 만들지 않는다.
        // 여기서는 "세션이 생성되고 SecurityContext가 저장됐는지"를 검증하고,
        // 실제 HttpOnly 쿠키 발급은 실서버 기동 후 수동 검증(curl/브라우저)으로 확인한다.
        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
    }

    @Test
    void 잘못된_비밀번호로_로그인하면_401을_반환한다() throws Exception {
        signup();

        String wrongPassword = """
                {
                  "username": "alice",
                  "password": "wrong-password"
                }
                """;
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(wrongPassword))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("bad-credentials"));
    }

    @Test
    void 없는_아이디로_로그인해도_같은_401_응답을_반환한다() throws Exception {
        // 계정 존재 여부가 노출되지 않도록 '없는 아이디'와 '비밀번호 불일치'가 같은 응답이어야 한다.
        String unknownUser = """
                {
                  "username": "ghost",
                  "password": "password123"
                }
                """;
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(unknownUser))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("bad-credentials"))
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void 로그인_없이_me를_조회하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void 로그인_후_me를_조회하면_내_정보를_반환한다() throws Exception {
        signup();
        MockHttpSession session = login();

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.nickname").value("앨리스"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void 로그아웃하면_204를_반환하고_세션이_무효화된다() throws Exception {
        signup();
        MockHttpSession session = login();

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void 세션_없이_로그아웃해도_204를_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }

    private void signup() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
