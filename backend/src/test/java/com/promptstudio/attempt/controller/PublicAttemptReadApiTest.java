package com.promptstudio.attempt.controller;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.domain.CodeRunView;
import com.promptstudio.attempt.service.AttemptService;
import com.promptstudio.attempt.service.CodeRunService;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import com.promptstudio.support.FakeCodeRunConfiguration;
import com.promptstudio.user.domain.User;
import com.promptstudio.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제출 완료된 어템프트는 누구나 읽고, 쓰기는 소유자만 한다.
 *
 * <p>랭킹이 남의 어템프트 ID를 공개하므로 "그 ID로 무엇을 할 수 있는가"가 곧 정책이다.
 * 기본 principal({@code _test_owner})은 남의 풀이를 구경하러 온 제3자 역할을 한다.
 */
@AutoConfigureMockMvc
@Import({FakeAiConfiguration.class, FakeCodeRunConfiguration.class, AttemptApiAuthenticationConfiguration.class})
class PublicAttemptReadApiTest extends DatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AttemptService attemptService;

    @Autowired
    private CodeRunService codeRunService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 코드와 프롬프트를 가리지 않는다 — 랭킹에서 남의 풀이를 열어 보는 것이 이 공개의 목적이다.
     */
    @Test
    void 다른_사용자도_제출된_어템프트를_조회할_수_있다() throws Exception {
        Long attemptId = submittedAttempt(AttemptOwner.user(newUser().id()));

        mockMvc.perform(get("/api/attempts/{id}", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.turns[0].prompt").value("Hello 출력해줘"))
                .andExpect(jsonPath("$.files[0].content").value("생성된 내용"));
    }

    @Test
    void 게스트도_제출된_어템프트를_조회할_수_있다() throws Exception {
        Long attemptId = submittedAttempt(AttemptOwner.user(newUser().id()));

        mockMvc.perform(get("/api/attempts/{id}", attemptId).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void 다른_사용자도_제출된_어템프트의_피드백을_조회할_수_있다() throws Exception {
        Long attemptId = submittedAttempt(AttemptOwner.user(newUser().id()));

        mockMvc.perform(get("/api/attempts/{id}/feedback", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallMd").value("생성된 피드백"));
    }

    @Test
    void 다른_사용자도_제출된_어템프트의_실행_기록을_조회할_수_있다() throws Exception {
        AttemptOwner other = AttemptOwner.user(newUser().id());
        Long attemptId = submittedAttempt(other);
        CodeRunView run = codeRunService.requestRun(attemptId, other);
        codeRunService.applyResult(new CodeRunResult(
                run.id(), CodeRunStatus.SUCCEEDED, 0, "Hello World\n", "", 900L));

        mockMvc.perform(get("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs.length()").value(1))
                .andExpect(jsonPath("$.runs[0].status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/attempts/{id}/runs/{runId}", attemptId, run.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stdout").value("Hello World\n"));
    }

    /**
     * 진행 중인 풀이는 403이 아니라 404다 — 남에게 존재를 알리지 않는다.
     */
    @Test
    void 다른_사용자는_진행_중인_어템프트를_조회할_수_없다() throws Exception {
        AttemptOwner other = AttemptOwner.user(newUser().id());
        Long attemptId = attemptService.startAttempt(newProblem().id(), other, null).id();

        mockMvc.perform(get("/api/attempts/{id}", attemptId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("attempt-not-found"));
        mockMvc.perform(get("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("attempt-not-found"));
    }

    @Test
    void 제출된_어템프트에도_쓰기는_소유자만_한다() throws Exception {
        Long attemptId = submittedAttempt(AttemptOwner.user(newUser().id()));

        mockMvc.perform(post("/api/attempts/{id}/turns", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"한 번 더 고쳐줘\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("attempt-not-found"));
        mockMvc.perform(post("/api/attempts/{id}/submit", attemptId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("attempt-not-found"));
        mockMvc.perform(post("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("attempt-not-found"));
    }

    private Long submittedAttempt(AttemptOwner owner) {
        AttemptView attempt = attemptService.startAttempt(newProblem().id(), owner, null);

        attemptService.addTurn(attempt.id(), owner, "Hello 출력해줘", null);
        attemptService.submit(attempt.id(), owner);

        return attempt.id();
    }

    private User newUser() {
        return userRepository.save(User.create(
                "other-" + System.nanoTime(), "{noop}password", "다른 사람", System.nanoTime() + "@example.com"));
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem(
                "public-read-" + UUID.randomUUID(),
                "Hello World 출력",
                "# 명세",
                List.of(new ProblemFile("src/main/java/Main.java", "class Main {}")),
                List.of()
        ));
    }
}
