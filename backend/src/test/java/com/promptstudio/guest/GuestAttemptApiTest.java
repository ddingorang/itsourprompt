package com.promptstudio.guest;

import com.jayway.jsonpath.JsonPath;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import jakarta.servlet.http.Cookie;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
class GuestAttemptApiTest extends DatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private DSLContext dsl;

    @Test
    void 비로그인_앱_진입에서_게스트_쿠키가_발급되고_풀이를_계속할_수_있다() throws Exception {
        Cookie guestCookie = issueGuestCookie();
        Long attemptId = createGuestAttempt(guestCookie);

        assertThat(dsl.fetchValue("SELECT user_id FROM attempt WHERE id = ?", attemptId)).isNull();
        assertThat(dsl.fetchValue("SELECT guest_session_id FROM attempt WHERE id = ?", attemptId)).isNotNull();

        mockMvc.perform(get("/api/attempts/{id}", attemptId).cookie(guestCookie))
                .andExpect(status().isOk());
    }

    @Test
    void 다른_게스트는_진행_중인_풀이를_조회할_수_없다() throws Exception {
        Long attemptId = createGuestAttempt(issueGuestCookie());

        mockMvc.perform(get("/api/attempts/{id}", attemptId))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_게스트는_진행_중인_풀이의_실행_목록도_조회할_수_없다() throws Exception {
        Long attemptId = createGuestAttempt(issueGuestCookie());

        mockMvc.perform(get("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isNotFound());
    }

    @Test
    void 게스트가_로그인하면_풀이가_회원_소유로_이전된다() throws Exception {
        Cookie guestCookie = issueGuestCookie();
        Long attemptId = createGuestAttempt(guestCookie);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"guest-owner","password":"password123","nickname":"게스트","email":"guest-owner@example.com"}
                                """))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(guestCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"username\":\"guest-owner\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Long userId = (Long) dsl.fetchValue("SELECT user_id FROM attempt WHERE id = ?", attemptId);
        assertThat(userId).isNotNull();
        assertThat(dsl.fetchValue("SELECT guest_session_id FROM attempt WHERE id = ?", attemptId)).isNull();
        assertThat(login.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("GUEST_SESSION=") && header.contains("Max-Age=0"));

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/attempts/{id}", attemptId).session(session))
                .andExpect(status().isOk());
    }

    private Cookie issueGuestCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("GUEST_SESSION=").contains("HttpOnly").contains("Max-Age=3600");
        return new Cookie("GUEST_SESSION", setCookie.substring("GUEST_SESSION=".length(), setCookie.indexOf(';')));
    }

    private Long createGuestAttempt(Cookie guestCookie) throws Exception {
        Problem problem = problemRepository.save(new Problem(
                "guest-problem-" + System.nanoTime(),
                "Guest problem",
                "# Guest problem",
                List.of(new ProblemFile("Main.java", "class Main {}")),
                List.of()
        ));
        String response = mockMvc.perform(post("/api/attempts")
                        .cookie(guestCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + problem.id() + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.parse(response).read("$.id", Long.class);
    }
}
