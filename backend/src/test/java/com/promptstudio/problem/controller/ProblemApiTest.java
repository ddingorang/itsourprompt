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
        problemRepository.save(newProblem("hello-world", "Hello World 출력"));
        problemRepository.save(newProblem("print-ssafy", "SSAFY 출력"));

        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems.length()").value(2))
                .andExpect(jsonPath("$.problems[0].title").value("Hello World 출력"))
                .andExpect(jsonPath("$.problems[1].title").value("SSAFY 출력"));
    }

    @Test
    void 문제_상세를_조회한다() throws Exception {
        Problem saved = problemRepository.save(newProblem("hello-world", "Hello World 출력"));

        mockMvc.perform(get("/api/problems/{id}", saved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.id()))
                .andExpect(jsonPath("$.title").value("Hello World 출력"))
                .andExpect(jsonPath("$.specMd").value("# Hello World 출력"))
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].path").value("src/main/java/Main.java"))
                .andExpect(jsonPath("$.files[0].content").value("class Main {}"));
    }

    /**
     * 채점용 테스트가 응답에 섞이면 사용자가 정답 조건을 그대로 보게 되고, 프롬프트로 옮겨 적으면
     * AI도 보게 된다. 노출 차단은 ProblemView가 testFiles를 읽지 않는 것뿐이라 여기서 못박아 둔다.
     */
    @Test
    void 문제_상세에_채점용_테스트는_포함하지_않는다() throws Exception {
        Problem saved = problemRepository.save(newProblem("hello-world", "Hello World 출력"));

        mockMvc.perform(get("/api/problems/{id}", saved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[?(@.path =~ /.*test.*/)]").isEmpty());
    }

    @Test
    void 없는_문제를_조회하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/problems/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("problem-not-found"));
    }

    @Test
    void 비활성_문제는_목록에서_제외한다() throws Exception {
        problemRepository.save(newProblem("hello-world", "Hello World 출력"));
        deactivated("print-ssafy", "SSAFY 출력");

        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems.length()").value(1))
                .andExpect(jsonPath("$.problems[0].title").value("Hello World 출력"));
    }

    @Test
    void 비활성_문제도_상세는_조회한다() throws Exception {
        Problem inactive = deactivated("print-ssafy", "SSAFY 출력");

        mockMvc.perform(get("/api/problems/{id}", inactive.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inactive.id()))
                .andExpect(jsonPath("$.title").value("SSAFY 출력"));
    }

    private Problem deactivated(String slug, String title) {
        Problem problem = problemRepository.save(newProblem(slug, title));
        problem.deactivate();

        return problemRepository.save(problem);
    }

    private Problem newProblem(String slug, String title) {
        return new Problem(slug, title, "# " + title, List.of(
                new ProblemFile("src/main/java/Main.java", "class Main {}")
        ), List.of(
                new ProblemFile("src/test/java/MainTest.java", "class MainTest {}")
        ));
    }
}
