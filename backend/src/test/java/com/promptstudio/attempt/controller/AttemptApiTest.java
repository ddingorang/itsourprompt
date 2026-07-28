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
                .andExpect(jsonPath("$.turns[0].changedFiles[0].changeType").value("MODIFIED"));
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
        return problemRepository.save(new Problem(null, "Hello World 출력", "# Hello World 출력", List.of(
                new ProblemFile("src/main/java/Main.java", "class Main {}")
        )));
    }
}
