package com.promptstudio.attempt.controller;

import com.jayway.jsonpath.JsonPath;
import com.promptstudio.attempt.domain.CodeRunCase;
import com.promptstudio.attempt.domain.CodeRunCaseStatus;
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
    void 턴이_없는_어템프트를_실행하면_turnOrdinal이_null이다() throws Exception {
        Long attemptId = createAttempt();

        mockMvc.perform(post("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.turnOrdinal").doesNotExist());
    }

    @Test
    void 턴을_지정해_실행하면_202와_그_턴_번호를_반환한다() throws Exception {
        Long attemptId = createAttempt();
        addTurn(attemptId);
        addTurn(attemptId);

        mockMvc.perform(post("/api/attempts/{id}/turns/{ordinal}/runs", attemptId, 0))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").isString())
                .andExpect(jsonPath("$.turnOrdinal").value(0))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void 턴을_지정하지_않으면_마지막_턴_번호를_반환한다() throws Exception {
        Long attemptId = createAttempt();
        addTurn(attemptId);
        addTurn(attemptId);

        mockMvc.perform(post("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.turnOrdinal").value(1));
    }

    @Test
    void 없는_턴을_지정하면_404다() throws Exception {
        Long attemptId = createAttempt();
        addTurn(attemptId);

        mockMvc.perform(post("/api/attempts/{id}/turns/{ordinal}/runs", attemptId, 5))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("turn-not-found"));
    }

    @Test
    void 없는_어템프트에_턴_지정_실행을_요청하면_404다() throws Exception {
        mockMvc.perform(post("/api/attempts/{id}/turns/{ordinal}/runs", 999, 0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("attempt-not-found"));
    }

    @Test
    void 실행_목록을_최근순으로_반환한다() throws Exception {
        Long attemptId = createAttempt();
        UUID first = requestRun(attemptId);
        // 어템프트당 미완료 실행은 하나뿐이라 앞 실행을 끝내야 다음 실행을 넣을 수 있다.
        codeRunService.applyResult(new CodeRunResult(first, CodeRunStatus.TEST_FAILED, 1, "", "", 900L));
        UUID second = requestRun(attemptId);

        mockMvc.perform(get("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs.length()").value(2))
                .andExpect(jsonPath("$.runs[0].runId").value(second.toString()))
                .andExpect(jsonPath("$.runs[0].status").value("QUEUED"))
                .andExpect(jsonPath("$.runs[1].runId").value(first.toString()))
                .andExpect(jsonPath("$.runs[1].status").value("TEST_FAILED"))
                .andExpect(jsonPath("$.runs[1].exitCode").value(1))
                .andExpect(jsonPath("$.runs[1].durationMs").value(900));
    }

    @Test
    void 실행이_없으면_빈_목록을_반환한다() throws Exception {
        Long attemptId = createAttempt();

        mockMvc.perform(get("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs").isArray())
                .andExpect(jsonPath("$.runs.length()").value(0));
    }

    /**
     * stdout·stderr는 각각 64KB까지 커질 수 있어 목록에 실으면 응답이 메가바이트가 된다.
     * 요약만 담는다는 계약을 여기서 못 박는다.
     */
    @Test
    void 목록에는_본문을_담지_않는다() throws Exception {
        Long attemptId = createAttempt();
        UUID runId = requestRun(attemptId);
        codeRunService.applyResult(new CodeRunResult(
                runId, CodeRunStatus.SUCCEEDED, 0, "출력 본문", "에러 본문", 1200L));

        mockMvc.perform(get("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs[0].stdout").doesNotExist())
                .andExpect(jsonPath("$.runs[0].stderr").doesNotExist())
                .andExpect(jsonPath("$.runs[0].createdAt").isString())
                .andExpect(jsonPath("$.runs[0].finishedAt").isString());
    }

    @Test
    void 턴을_지정해_실행한_기록은_목록에_그_턴_번호로_남는다() throws Exception {
        Long attemptId = createAttempt();
        addTurn(attemptId);

        mockMvc.perform(post("/api/attempts/{id}/turns/{ordinal}/runs", attemptId, 0))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs[0].turnOrdinal").value(0));
    }

    @Test
    void 없는_어템프트의_목록을_조회하면_404다() throws Exception {
        mockMvc.perform(get("/api/attempts/{id}/runs", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("attempt-not-found"));
    }

    @Test
    void 채점_케이스가_단건_조회에_담긴다() throws Exception {
        Long attemptId = createAttempt();
        UUID runId = requestRun(attemptId);

        codeRunService.applyResult(new CodeRunResult(
                runId, CodeRunStatus.TEST_FAILED, 1, "트리 출력", "", 1200L,
                List.of(
                        new CodeRunCase("PhoneNumberFormatterTest", "휴대전화_번호를_마스킹한다()",
                                CodeRunCaseStatus.PASSED, null, 30L),
                        new CodeRunCase("PhoneNumberFormatterTest", "지역번호_2자리와_국번_3자리를_마스킹한다()",
                                CodeRunCaseStatus.FAILED, "expected: <02-***-4567> but was: <02-1****567>", 17L))));

        mockMvc.perform(get("/api/attempts/{id}/runs/{runId}", attemptId, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TEST_FAILED"))
                // 원문은 그대로 유지한다. 컴파일 오류·예외는 케이스에 안 나오므로 진단에 필요하다.
                .andExpect(jsonPath("$.stdout").value("트리 출력"))
                .andExpect(jsonPath("$.cases.length()").value(2))
                .andExpect(jsonPath("$.cases[0].name").value("휴대전화_번호를_마스킹한다()"))
                .andExpect(jsonPath("$.cases[0].className").value("PhoneNumberFormatterTest"))
                .andExpect(jsonPath("$.cases[0].status").value("PASSED"))
                .andExpect(jsonPath("$.cases[0].message").doesNotExist())
                .andExpect(jsonPath("$.cases[1].status").value("FAILED"))
                .andExpect(jsonPath("$.cases[1].message")
                        .value("expected: <02-***-4567> but was: <02-1****567>"))
                .andExpect(jsonPath("$.cases[1].durationMs").value(17));
    }

    @Test
    void 케이스가_없는_실행은_빈_배열이다() throws Exception {
        Long attemptId = createAttempt();
        UUID runId = requestRun(attemptId);
        codeRunService.applyResult(new CodeRunResult(runId, CodeRunStatus.SUCCEEDED, 0, "hi", "", 900L));

        mockMvc.perform(get("/api/attempts/{id}/runs/{runId}", attemptId, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases").isArray())
                .andExpect(jsonPath("$.cases.length()").value(0));
    }

    /**
     * 목록은 배지를 그릴 집계만 받는다. 케이스 배열을 목록에 실으면 stdout을 뺀 이유를 그대로 반복한다.
     */
    @Test
    void 목록에는_케이스_집계만_담긴다() throws Exception {
        Long attemptId = createAttempt();
        UUID runId = requestRun(attemptId);

        codeRunService.applyResult(new CodeRunResult(
                runId, CodeRunStatus.TEST_FAILED, 1, "", "", 1200L,
                List.of(
                        new CodeRunCase("T", "통과1()", CodeRunCaseStatus.PASSED, null, 1L),
                        new CodeRunCase("T", "통과2()", CodeRunCaseStatus.PASSED, null, 1L),
                        new CodeRunCase("T", "실패()", CodeRunCaseStatus.FAILED, "diff", 1L),
                        new CodeRunCase("T", "에러()", CodeRunCaseStatus.ERROR, "boom", 1L),
                        new CodeRunCase("T", "스킵()", CodeRunCaseStatus.SKIPPED, null, null))));

        mockMvc.perform(get("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs[0].tally.total").value(5))
                .andExpect(jsonPath("$.runs[0].tally.passed").value(2))
                .andExpect(jsonPath("$.runs[0].tally.failed").value(1))
                .andExpect(jsonPath("$.runs[0].tally.error").value(1))
                .andExpect(jsonPath("$.runs[0].tally.skipped").value(1))
                .andExpect(jsonPath("$.runs[0].cases").doesNotExist());
    }

    @Test
    void 케이스_기록이_없는_실행은_목록_집계가_null이다() throws Exception {
        Long attemptId = createAttempt();

        mockMvc.perform(post("/api/attempts/{id}/runs", attemptId)).andExpect(status().isAccepted());

        mockMvc.perform(get("/api/attempts/{id}/runs", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs[0].tally").doesNotExist());
    }

    /**
     * 결과 반영은 멱등해야 한다. 이미 종료된 run에 결과가 다시 도착해도 케이스가 중복으로 쌓이면 안 된다.
     */
    @Test
    void 결과가_두_번_도착해도_케이스가_중복되지_않는다() throws Exception {
        Long attemptId = createAttempt();
        UUID runId = requestRun(attemptId);
        CodeRunResult result = new CodeRunResult(
                runId, CodeRunStatus.SUCCEEDED, 0, "", "", 900L,
                List.of(new CodeRunCase("T", "통과()", CodeRunCaseStatus.PASSED, null, 1L)));

        codeRunService.applyResult(result);
        codeRunService.applyResult(result);

        mockMvc.perform(get("/api/attempts/{id}/runs/{runId}", attemptId, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases.length()").value(1));
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

    private void addTurn(Long attemptId) throws Exception {
        mockMvc.perform(post("/api/attempts/{id}/turns", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"코드를 완성해줘\"}"))
                .andExpect(status().isOk());
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
