package com.promptstudio.attempt.controller;

import com.jayway.jsonpath.JsonPath;
import com.promptstudio.attempt.port.CodeGenerationException;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotency-Key HTTP 심. 처리 중인 요청(PENDING)은 사전 행 삽입으로 시뮬레이션한다 — MockMvc는 동기다.
 */
@AutoConfigureMockMvc
@Import({FakeAiConfiguration.class, AttemptApiAuthenticationConfiguration.class})
class IdempotencyApiTest extends DatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private FakeAiConfiguration.FakeCodeGenerator codeGenerator;

    @BeforeEach
    void 가짜_생성기를_초기화한다() {
        codeGenerator.reset();
    }

    @Test
    void Idempotency_Key를_보내면_턴_추가_후_COMPLETED로_기록된다() throws Exception {
        Long attemptId = createAttempt();

        addTurn(attemptId, "key-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turns.length()").value(1));

        Result<Record> records = findRecords("key-1");
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().get("status", String.class)).isEqualTo("COMPLETED");
        assertThat(records.getFirst().get("attempt_id", Long.class)).isEqualTo(attemptId);
    }

    @Test
    void Idempotency_Key_없이_요청하면_기존과_동일하게_동작한다() throws Exception {
        Long attemptId = createAttempt();

        addTurn(attemptId, null).andExpect(status().isOk());
        addTurn(attemptId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turns.length()").value(2));

        assertThat(findAllRecords()).isEmpty();
    }

    @Test
    void 같은_Idempotency_Key로_턴을_다시_요청하면_AI_호출_없이_같은_상태를_반환한다() throws Exception {
        Long attemptId = createAttempt();

        addTurn(attemptId, "key-1").andExpect(status().isOk());
        addTurn(attemptId, "key-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attemptId))
                .andExpect(jsonPath("$.turns.length()").value(1));

        assertThat(codeGenerator.invocationCount()).isEqualTo(1);
    }

    @Test
    void 처리_중인_Idempotency_Key로_요청하면_409를_반환한다() throws Exception {
        Long attemptId = createAttempt();
        insertPendingRow("key-1");

        addTurn(attemptId, "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-request"));

        assertThat(codeGenerator.invocationCount()).isZero();
    }

    @Test
    void 오래된_PENDING_Idempotency_Key로_요청하면_재실행된다() throws Exception {
        Long attemptId = createAttempt();
        insertStalePendingRow("key-1");

        addTurn(attemptId, "key-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turns.length()").value(1));

        assertThat(codeGenerator.invocationCount()).isEqualTo(1);
        assertThat(findRecords("key-1").getFirst().get("status", String.class)).isEqualTo("COMPLETED");
    }

    @Test
    void 같은_Idempotency_Key로_어템프트_생성을_다시_요청하면_같은_어템프트를_반환한다() throws Exception {
        Long problemId = newProblem().id();

        Long first = createAttempt(problemId, "key-1");
        Long second = createAttempt(problemId, "key-1");

        assertThat(second).isEqualTo(first);
        assertThat(dsl.fetch("SELECT id FROM attempt")).hasSize(1);
    }

    @Test
    void AI_호출이_실패하면_키가_삭제되어_같은_Idempotency_Key로_재시도할_수_있다() throws Exception {
        Long attemptId = createAttempt();
        codeGenerator.failNextWith(new CodeGenerationException("AI 호출에 실패했습니다."));

        addTurn(attemptId, "key-1").andExpect(status().isBadGateway());

        assertThat(findRecords("key-1")).isEmpty();

        addTurn(attemptId, "key-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turns.length()").value(1));

        assertThat(codeGenerator.invocationCount()).isEqualTo(2);
    }

    private void insertStalePendingRow(String idempotencyKey) {
        dsl.execute(
                "INSERT INTO idempotency_record (idempotency_key, user_id, status, created_at)"
                        + " VALUES (?, ?, 'PENDING', now() - interval '10 minutes')",
                scopedKey(idempotencyKey), ownerId
        );
    }

    private void insertPendingRow(String idempotencyKey) {
        dsl.execute(
                "INSERT INTO idempotency_record (idempotency_key, user_id, status, created_at) VALUES (?, ?, 'PENDING', now())",
                scopedKey(idempotencyKey), ownerId
        );
    }

    private org.springframework.test.web.servlet.ResultActions addTurn(Long attemptId, String idempotencyKey)
            throws Exception {
        var request = post("/api/attempts/{id}/turns", attemptId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"Hello 출력해줘\"}");

        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }

        return mockMvc.perform(request);
    }

    private Result<Record> findRecords(String idempotencyKey) {
        return dsl.fetch("SELECT status, attempt_id FROM idempotency_record WHERE idempotency_key = ?", scopedKey(idempotencyKey));
    }

    private Result<Record> findAllRecords() {
        return dsl.fetch("SELECT idempotency_key FROM idempotency_record");
    }

    private String scopedKey(String idempotencyKey) {
        return "user:" + ownerId + ":" + idempotencyKey;
    }

    private Long createAttempt() throws Exception {
        return createAttempt(newProblem().id(), null);
    }

    private Long createAttempt(Long problemId, String idempotencyKey) throws Exception {
        var request = post("/api/attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemId\":" + problemId + "}");

        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }

        String response = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.parse(response).read("$.id", Long.class);
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem(null, "Hello World 출력", "# Hello World 출력", List.of(
                new ProblemFile("src/main/java/Main.java", "class Main {}")
        ), List.of()));
    }
}
