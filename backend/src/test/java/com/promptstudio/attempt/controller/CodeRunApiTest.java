package com.promptstudio.attempt.controller;

import com.jayway.jsonpath.JsonPath;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.service.CodeRunService;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import com.promptstudio.support.FakeCodeRunConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import({FakeAiConfiguration.class, FakeCodeRunConfiguration.class, AttemptApiAuthenticationConfiguration.class})
class CodeRunApiTest extends DatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private CodeRunService codeRunService;

    @Autowired
    private FakeCodeRunConfiguration.FakeCodeRunPublisher publisher;

    @BeforeEach
    void 발행_기록을_비운다() {
        publisher.reset();
    }

    @Test
    void 실행을_요청하면_202와_실행_ID를_반환한다() throws Exception {
        Long attemptId = createAttempt();

        mockMvc.perform(post("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").isString())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.exitCode").doesNotExist())
                .andExpect(jsonPath("$.stdout").doesNotExist());
    }

    @Test
    void 없는_어템프트로_요청하면_404다() throws Exception {
        mockMvc.perform(post("/api/attempts/{id}/runs", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("attempt-not-found"));
    }

    @Test
    void 실행이_진행_중이면_409다() throws Exception {
        Long attemptId = createAttempt();
        mockMvc.perform(post("/api/attempts/{id}/runs", attemptId)).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("code-run-in-progress"));
    }

    @Test
    void 결과가_반영되면_조회에서_확인된다() throws Exception {
        Long attemptId = createAttempt();
        UUID runId = requestRun(attemptId);

        codeRunService.applyResult(new CodeRunResult(
                runId, CodeRunStatus.SUCCEEDED, 0, "Hello World\n", "", 1840L));

        mockMvc.perform(get("/api/attempts/{id}/runs/{runId}", attemptId, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.exitCode").value(0))
                .andExpect(jsonPath("$.stdout").value("Hello World\n"))
                .andExpect(jsonPath("$.durationMs").value(1840));
    }

    @Test
    void 컴파일_실패는_stderr와_함께_조회된다() throws Exception {
        Long attemptId = createAttempt();
        UUID runId = requestRun(attemptId);

        codeRunService.applyResult(new CodeRunResult(
                runId, CodeRunStatus.COMPILE_ERROR, 1, "", "Main.java:3: error: ';' expected", 420L));

        mockMvc.perform(get("/api/attempts/{id}/runs/{runId}", attemptId, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_ERROR"))
                .andExpect(jsonPath("$.stderr").value("Main.java:3: error: ';' expected"));
    }

    @Test
    void 없는_실행_ID를_조회하면_404다() throws Exception {
        Long attemptId = createAttempt();

        mockMvc.perform(get("/api/attempts/{id}/runs/{runId}", attemptId, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("code-run-not-found"));
    }

    private UUID requestRun(Long attemptId) throws Exception {
        String body = mockMvc.perform(post("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(JsonPath.read(body, "$.runId"));
    }

    private Long createAttempt() throws Exception {
        Long problemId = newProblem().id();
        String body = mockMvc.perform(post("/api/attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + problemId + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem(
                "hello-world-" + UUID.randomUUID(),
                "Hello World 출력",
                "# 명세",
                List.of(new ProblemFile("src/main/java/Main.java", "class Main {}")),
                List.of()
        ));
    }
}
