package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemSeederTest extends DatabaseTest {

    @Autowired
    private ProblemSeeder problemSeeder;

    @Autowired
    private ProblemRepository problemRepository;

    @Test
    void 빈_DB면_3문제를_삽입한다() {
        problemSeeder.seed();

        assertThat(problemRepository.findAll())
                .extracting(Problem::title)
                .containsExactly("Hello World 출력", "SSAFY 출력", "환영 메시지 출력");
    }

    @Test
    void 이미_문제가_있으면_삽입하지_않는다() {
        problemRepository.save(new Problem(null, "기존 문제", "명세", List.of(
                new ProblemFile("src/Main.java", "class Main {}")
        )));

        problemSeeder.seed();

        assertThat(problemRepository.findAll())
                .extracting(Problem::title)
                .containsExactly("기존 문제");
    }
}
