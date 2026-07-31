package com.promptstudio.attempt.controller;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.repository.AttemptRepository;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.user.domain.User;
import com.promptstudio.user.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(AttemptApiAuthenticationConfiguration.class)
class MySubmittedAttemptsApiTest extends DatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DSLContext dsl;

    @Test
    void 로그인하지_않으면_내_제출_목록을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/me/attempts").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 내_제출을_모두_제출_시각_내림차순으로_반환한다() throws Exception {
        Problem firstProblem = newProblem("첫 번째 문제");
        Problem repeatedProblem = newProblem("반복 제출 문제");

        Attempt oldest = submittedAttempt(firstProblem, ownerId);
        Attempt middle = submittedAttempt(repeatedProblem, ownerId);
        Attempt newest = submittedAttempt(repeatedProblem, ownerId);
        Attempt inProgress = attemptRepository.save(Attempt.start(newProblem("풀이 중 문제"), ownerId));

        User otherUser = userRepository.save(User.create(
                "other-user", "{noop}password", "other", "other@example.com"));
        Attempt otherUsersAttempt = submittedAttempt(newProblem("다른 사용자 문제"), otherUser.id());

        setSubmittedAt(oldest, "2026-07-01T01:00:00Z");
        setSubmittedAt(middle, "2026-07-02T01:00:00Z");
        setSubmittedAt(newest, "2026-07-03T01:00:00Z");
        setSubmittedAt(otherUsersAttempt, "2026-07-04T01:00:00Z");

        mockMvc.perform(get("/api/me/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].attemptId").value(newest.id()))
                .andExpect(jsonPath("$[0].problemId").value(repeatedProblem.id()))
                .andExpect(jsonPath("$[0].problemTitle").value("반복 제출 문제"))
                .andExpect(jsonPath("$[0].submittedAt").value("2026-07-03T01:00:00Z"))
                .andExpect(jsonPath("$[1].attemptId").value(middle.id()))
                .andExpect(jsonPath("$[2].attemptId").value(oldest.id()))
                .andExpect(jsonPath("$[?(@.attemptId == " + inProgress.id() + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.attemptId == " + otherUsersAttempt.id() + ")]").isEmpty());
    }

    private Attempt submittedAttempt(Problem problem, Long userId) {
        Attempt attempt = attemptRepository.save(Attempt.start(problem, userId));
        attempt.submit(new AttemptFeedback(List.of(), "피드백", List.of()));
        return attemptRepository.save(attempt);
    }

    private void setSubmittedAt(Attempt attempt, String submittedAt) {
        dsl.execute("UPDATE attempt SET submitted_at = ? WHERE id = ?", Instant.parse(submittedAt), attempt.id());
    }

    private Problem newProblem(String title) {
        return problemRepository.save(new Problem(
                null, title, "명세", List.of(new ProblemFile("src/Main.java", "class Main {}"))));
    }
}
