package com.promptstudio.attempt.controller;

import com.jayway.jsonpath.JsonPath;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
class AttemptApiTest extends DatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemRepository problemRepository;

    @Test
    void 어템프트를_생성하면_문제_스켈레톤으로_초기화된다() throws Exception {
        Long problemId = newProblem().id();

        mockMvc.perform(post("/api/attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + problemId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.problemId").value(problemId))
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].path").value("src/main/java/Main.java"))
                .andExpect(jsonPath("$.files[0].content").value("class Main {}"))
                .andExpect(jsonPath("$.turns.length()").value(0));
    }

    @Test
    void 턴을_추가하면_AI_결과가_반영된다() throws Exception {
        Long attemptId = createAttempt();

        mockMvc.perform(post("/api/attempts/{id}/turns", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello 출력해줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attemptId))
                .andExpect(jsonPath("$.files[0].content").value("생성된 내용"))
                .andExpect(jsonPath("$.turns.length()").value(1))
                .andExpect(jsonPath("$.turns[0].prompt").value("Hello 출력해줘"))
                .andExpect(jsonPath("$.turns[0].aiResponse").value("생성 요약"))
                .andExpect(jsonPath("$.turns[0].changedFiles[0].path").value("src/main/java/Main.java"))
                .andExpect(jsonPath("$.turns[0].changedFiles[0].changeType").value("MODIFIED"))
                .andExpect(jsonPath("$.turns[0].changedFiles[0].content").value("생성된 내용"));
    }

    @Test
    void 턴을_추가해도_시작_스켈레톤은_그대로_반환된다() throws Exception {
        Long attemptId = createAttempt();
        addTurn(attemptId);

        mockMvc.perform(get("/api/attempts/{id}", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseFiles.length()").value(1))
                .andExpect(jsonPath("$.baseFiles[0].path").value("src/main/java/Main.java"))
                .andExpect(jsonPath("$.baseFiles[0].content").value("class Main {}"))
                .andExpect(jsonPath("$.files[0].content").value("생성된 내용"));
    }

    @Test
    void 추가한_턴이_조회에서_그대로_반환된다() throws Exception {
        Long attemptId = createAttempt();
        mockMvc.perform(post("/api/attempts/{id}/turns", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello 출력해줘\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/attempts/{id}", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attemptId))
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].content").value("생성된 내용"))
                .andExpect(jsonPath("$.turns.length()").value(1))
                .andExpect(jsonPath("$.turns[0].prompt").value("Hello 출력해줘"))
                .andExpect(jsonPath("$.turns[0].aiResponse").value("생성 요약"))
                .andExpect(jsonPath("$.turns[0].changedFiles[0].changeType").value("MODIFIED"));
    }

    @Test
    void 어템프트를_조회하면_상태가_포함되고_피드백은_없다() throws Exception {
        Long attemptId = createAttempt();

        mockMvc.perform(get("/api/attempts/{id}", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.feedback").doesNotExist());
    }

    @Test
    void 제출하면_피드백을_반환하고_상태가_반영된다() throws Exception {
        Long attemptId = createAttempt();
        addTurn(attemptId);

        mockMvc.perform(post("/api/attempts/{id}/submit", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("생성된 피드백"));

        mockMvc.perform(get("/api/attempts/{id}", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.feedback").doesNotExist());
    }

    @Test
    void 제출된_어템프트의_피드백을_조회한다() throws Exception {
        Long attemptId = createAttempt();
        addTurn(attemptId);
        mockMvc.perform(post("/api/attempts/{id}/submit", attemptId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/attempts/{id}/feedback", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("생성된 피드백"));
    }

    @Test
    void 제출_전에는_피드백_조회가_404를_반환한다() throws Exception {
        Long attemptId = createAttempt();
        addTurn(attemptId);

        mockMvc.perform(get("/api/attempts/{id}/feedback", attemptId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("feedback-not-found"));
    }

    @Test
    void 없는_어템프트의_피드백을_조회하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/attempts/{id}/feedback", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("attempt-not-found"));
    }

    @Test
    void 다시_제출해도_저장된_피드백을_반환한다() throws Exception {
        Long attemptId = createAttempt();
        addTurn(attemptId);
        mockMvc.perform(post("/api/attempts/{id}/submit", attemptId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/attempts/{id}/submit", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("생성된 피드백"));
    }

    @Test
    void 턴이_없으면_제출이_거부된다() throws Exception {
        Long attemptId = createAttempt();

        mockMvc.perform(post("/api/attempts/{id}/submit", attemptId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("attempt-has-no-turns"));
    }

    @Test
    void 제출된_어템프트에_턴을_추가하면_409를_반환한다() throws Exception {
        Long attemptId = createAttempt();
        addTurn(attemptId);
        mockMvc.perform(post("/api/attempts/{id}/submit", attemptId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/attempts/{id}/turns", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"한 번 더 고쳐줘\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("attempt-already-submitted"));
    }

    @Test
    void 비활성_문제로_어템프트를_생성하면_409를_반환한다() throws Exception {
        Problem problem = newProblem();
        problem.deactivate();
        problemRepository.save(problem);

        mockMvc.perform(post("/api/attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + problem.id() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("problem-inactive"));
    }

    private void addTurn(Long attemptId) throws Exception {
        mockMvc.perform(post("/api/attempts/{id}/turns", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello 출력해줘\"}"))
                .andExpect(status().isOk());
    }

    private Long createAttempt() throws Exception {
        String response = mockMvc.perform(post("/api/attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + newProblem().id() + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.parse(response).read("$.id", Long.class);
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem("hello-world", "Hello World 출력", "# Hello World 출력", List.of(
                new ProblemFile("src/main/java/Main.java", "class Main {}")
        )));
    }
}
