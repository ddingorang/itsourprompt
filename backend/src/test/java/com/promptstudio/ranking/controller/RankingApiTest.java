package com.promptstudio.ranking.controller;

import com.jayway.jsonpath.JsonPath;
import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.service.AttemptService;
import com.promptstudio.global.security.AppUserDetails;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import com.promptstudio.user.domain.User;
import com.promptstudio.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.jooq.DSLContext;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
class RankingApiTest extends DatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AttemptService attemptService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DSLContext dsl;

    @Test
    void 랭킹을_비용_오름차순으로_반환한다() throws Exception {
        Problem problem = newProblem();
        Long cheap = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        Long expensive = submittedAttempt(problem, AttemptOwner.user(ownerId), 2);

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problemId").value(problem.id()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].ownerLabel").value("test owner"))
                .andExpect(jsonPath("$.entries[0].cost").value(0.003))
                .andExpect(jsonPath("$.entries[0].turns").value(1))
                .andExpect(jsonPath("$.entries[0].rounds").value(2))
                .andExpect(jsonPath("$.entries[1].rank").value(2))
                .andExpect(jsonPath("$.entries[1].cost").value(0.006));

        assertThat(cheap).isNotEqualTo(expensive);
    }

    @Test
    void 익명_방문자에게는_내_순위가_없고_게스트_쿠키도_발급하지_않는다() throws Exception {
        Problem problem = newProblem();
        Long attemptId = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myBest").doesNotExist())
                .andExpect(jsonPath("$.entries[0].mine").value(false))
                .andExpect(jsonPath("$.entries[0].attemptId").value(attemptId))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        // 구경만 한 방문자에게 게스트 세션 행이 생기면 안 된다.
        assertThat(dsl.fetchCount(table(name("guest_session")))).isZero();
    }

    @Test
    void 로그인_사용자에게는_내_최고_기록을_함께_준다() throws Exception {
        Problem problem = newProblem();
        User other = newUser();
        Long others = submittedAttempt(problem, AttemptOwner.user(other.id()), 1);
        Long mine = submittedAttempt(problem, AttemptOwner.user(ownerId), 2);

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).with(user(principalOf(ownerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myBest.attemptId").value(mine))
                .andExpect(jsonPath("$.myBest.rank").value(2))
                .andExpect(jsonPath("$.myBest.mine").value(true))
                // 상단 목록에 이미 있어도 myBest는 항상 채우고, 중복 여부는 mine 플래그로 판단한다.
                .andExpect(jsonPath("$.entries[1].attemptId").value(mine))
                .andExpect(jsonPath("$.entries[1].mine").value(true))
                .andExpect(jsonPath("$.entries[0].mine").value(false))
                .andExpect(jsonPath("$.entries[0].attemptId").value(others));
    }

    /**
     * 어템프트 1건이 1줄이라 한 사람이 여러 줄을 차지한다. mine은 "내 최고 기록인가"가 아니라
     * "내 줄인가"여야 한다 — 최고 한 줄에만 붙으면 내 두 번째 기록이 남의 줄과 구분되지 않는다.
     */
    @Test
    void 상위에_오른_내_풀이가_여럿이면_모두_내_줄로_표시한다() throws Exception {
        Problem problem = newProblem();
        Long cheaper = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        Long pricier = submittedAttempt(problem, AttemptOwner.user(ownerId), 2);
        Long others = submittedAttempt(problem, AttemptOwner.user(newUser().id()), 3);

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).with(user(principalOf(ownerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].mine").value(true))
                .andExpect(jsonPath("$.entries[0].attemptId").value(cheaper))
                .andExpect(jsonPath("$.entries[1].mine").value(true))
                .andExpect(jsonPath("$.entries[1].attemptId").value(pricier))
                .andExpect(jsonPath("$.entries[2].mine").value(false))
                .andExpect(jsonPath("$.entries[2].attemptId").value(others))
                .andExpect(jsonPath("$.myBest.attemptId").value(cheaper));
    }

    @Test
    void 게스트_쿠키가_있어도_내_최고_기록은_없다() throws Exception {
        Problem problem = newProblem();
        Cookie guestCookie = issueGuestCookie();
        createGuestAttempt(problem, guestCookie);

        // 자격 조건은 모두 갖췄지만 게스트라 랭킹에 들지 않는다. 남은 길은 로그인뿐이다.
        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).cookie(guestCookie).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.myBest").doesNotExist());
    }

    @Test
    void 랭킹에_든_내_풀이가_없으면_내_순위는_null이다() throws Exception {
        Problem problem = newProblem();
        submittedAttempt(problem, AttemptOwner.user(newUser().id()), 1);

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).with(user(principalOf(ownerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myBest").doesNotExist());
    }

    @Test
    void 랭킹_줄에_소요_시간을_초로_싣는다() throws Exception {
        Problem problem = newProblem();
        Long mine = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        Long others = submittedAttempt(problem, AttemptOwner.user(newUser().id()), 1);
        setCodeCallsCreatedAt(mine, "2026-08-01T00:00:00Z");
        setSubmittedAt(mine, "2026-08-01T00:04:12Z");
        // 제출 시각을 모르는 옛 기록은 소요 시간도 모른다.
        dsl.execute("UPDATE attempt SET submitted_at = NULL WHERE id = ?", others);

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).with(user(principalOf(ownerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].attemptId").value(mine))
                .andExpect(jsonPath("$.entries[0].durationSeconds").value(252))
                .andExpect(jsonPath("$.myBest.durationSeconds").value(252))
                .andExpect(jsonPath("$.entries[1].attemptId").value(others))
                // doesNotExist()가 아니라 value(nullValue())다 — 전자는 필드가 통째로 사라져도 통과한다.
                // 화면은 "모름"과 "필드 없음"을 가려야 하므로 null이 실려 오는 것까지가 계약이다.
                .andExpect(jsonPath("$.entries[1].durationSeconds").value(nullValue()));
    }

    @Test
    void 자격을_갖춘_제출이_없으면_빈_목록을_준다() throws Exception {
        Problem problem = newProblem();

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.myBest").doesNotExist());
    }

    @Test
    void 없는_문제는_404다() throws Exception {
        mockMvc.perform(get("/api/problems/{id}/ranking", 99_999L).with(anonymous()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("problem-not-found"));
    }

    @Test
    void 비활성_문제도_랭킹은_보여준다() throws Exception {
        Problem problem = newProblem();
        submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        dsl.execute("UPDATE problem SET active = FALSE WHERE id = ?", problem.id());

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void limit으로_줄_수를_줄여도_전체_수는_그대로다() throws Exception {
        Problem problem = newProblem();
        submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        submittedAttempt(problem, AttemptOwner.user(ownerId), 2);

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).param("limit", "1").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    void 허용_범위를_벗어난_limit은_400이다() throws Exception {
        Problem problem = newProblem();

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).param("limit", "0").with(anonymous()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));
        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).param("limit", "51").with(anonymous()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));
        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).param("limit", "50").with(anonymous()))
                .andExpect(status().isOk());
    }

    @Test
    void 숫자가_아닌_limit도_400이다() throws Exception {
        Problem problem = newProblem();

        mockMvc.perform(get("/api/problems/{id}/ranking", problem.id()).param("limit", "abc").with(anonymous()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));
    }

    private Long submittedAttempt(Problem problem, AttemptOwner owner, int turns) {
        AttemptView attempt = attemptService.startAttempt(problem.id(), owner, null);

        for (int index = 0; index < turns; index++) {
            attemptService.addTurn(attempt.id(), owner, "턴 " + index, null);
        }

        attemptService.submit(attempt.id(), owner);
        succeedRun(attempt.id(), turns - 1);

        return attempt.id();
    }

    /** 게스트는 API로 만들어야 쿠키와 세션 행이 실제로 이어진다. */
    private Long createGuestAttempt(Problem problem, Cookie guestCookie) throws Exception {
        String created = mockMvc.perform(post("/api/attempts")
                        .cookie(guestCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + problem.id() + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long attemptId = JsonPath.parse(created).read("$.id", Long.class);

        mockMvc.perform(post("/api/attempts/{id}/turns", attemptId)
                        .cookie(guestCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello 출력해줘\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/attempts/{id}/submit", attemptId).cookie(guestCookie))
                .andExpect(status().isOk());
        succeedRun(attemptId, 0);

        return attemptId;
    }

    private Cookie issueGuestCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        return new Cookie("GUEST_SESSION", setCookie.substring("GUEST_SESSION=".length(), setCookie.indexOf(';')));
    }

    private void setSubmittedAt(Long attemptId, String submittedAt) {
        dsl.update(table(name("attempt")))
                .set(field(name("attempt", "submitted_at"), SQLDataType.INSTANT), Instant.parse(submittedAt))
                .where(field(name("attempt", "id"), SQLDataType.BIGINT).eq(attemptId))
                .execute();
    }

    private void setCodeCallsCreatedAt(Long attemptId, String createdAt) {
        dsl.update(table(name("attempt_llm_call")))
                .set(field(name("attempt_llm_call", "created_at"), SQLDataType.INSTANT), Instant.parse(createdAt))
                .where(field(name("attempt_llm_call", "attempt_id"), SQLDataType.BIGINT).eq(attemptId))
                .and(field(name("attempt_llm_call", "purpose"), SQLDataType.VARCHAR).eq("CODE"))
                .execute();
    }

    private void succeedRun(Long attemptId, int turnOrdinal) {
        dsl.execute(
                "INSERT INTO code_run (id, attempt_id, turn_ordinal, status, created_at)"
                        + " VALUES (?, ?, ?, 'SUCCEEDED', now())",
                UUID.randomUUID(), attemptId, turnOrdinal
        );
    }

    private AppUserDetails principalOf(Long userId) {
        return new AppUserDetails(userRepository.findById(userId).orElseThrow());
    }

    private User newUser() {
        return userRepository.save(User.create(
                "other-" + System.nanoTime(), "{noop}password", "다른 사람", System.nanoTime() + "@example.com"));
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem(
                "ranking-api-" + System.nanoTime(),
                "랭킹 문제",
                "명세",
                List.of(new ProblemFile("src/main/java/Main.java", "class Main {}")),
                List.of()
        ));
    }
}
