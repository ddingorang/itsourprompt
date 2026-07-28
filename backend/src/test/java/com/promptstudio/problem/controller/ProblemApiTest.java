package com.promptstudio.problem.controller;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
class ProblemApiTest extends DatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemRepository problemRepository;

    @Test
    void 문제_목록을_조회한다() throws Exception {
        problemRepository.save(newProblem("Hello World 출력"));
        problemRepository.save(newProblem("SSAFY 출력"));

        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems.length()").value(2))
                .andExpect(jsonPath("$.problems[0].title").value("Hello World 출력"))
                .andExpect(jsonPath("$.problems[1].title").value("SSAFY 출력"));
    }

    @Test
    void 문제_상세를_조회한다() throws Exception {
        Problem saved = problemRepository.save(newProblem("Hello World 출력"));

        mockMvc.perform(get("/api/problems/{id}", saved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.id()))
                .andExpect(jsonPath("$.title").value("Hello World 출력"))
                .andExpect(jsonPath("$.specMd").value("# Hello World 출력"))
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].path").value("src/main/java/Main.java"))
                .andExpect(jsonPath("$.files[0].content").value("class Main {}"));
    }

    @Test
    void 없는_문제를_조회하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/problems/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("problem-not-found"));
    }

    private Problem newProblem(String title) {
        return new Problem(null, title, "# " + title, List.of(
                new ProblemFile("src/main/java/Main.java", "class Main {}")
        ));
    }
}
