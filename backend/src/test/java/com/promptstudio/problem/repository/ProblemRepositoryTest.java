package com.promptstudio.problem.repository;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.support.DatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemRepositoryTest extends DatabaseTest {

    @Autowired
    private ProblemRepository problemRepository;

    @Test
    void 저장한_문제를_ID로_조회한다() {
        Problem saved = problemRepository.save(newProblem("Hello World 출력"));

        Problem found = problemRepository.findById(saved.id()).orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.title()).isEqualTo("Hello World 출력");
        assertThat(found.specMd()).isEqualTo("# Hello World 출력");
        assertThat(found.files()).containsExactly(new ProblemFile("src/Main.java", "class Main {}"));
    }

    @Test
    void 저장하면_DB가_ID를_발급한다() {
        Problem first = problemRepository.save(newProblem("첫 번째"));
        Problem second = problemRepository.save(newProblem("두 번째"));

        assertThat(first.id()).isNotNull();
        assertThat(second.id()).isGreaterThan(first.id());
    }

    @Test
    void 파일_순서를_보존한다() {
        Problem saved = problemRepository.save(new Problem("hello-world", "제목", "명세", List.of(
                new ProblemFile("src/Main.java", "class Main {}"),
                new ProblemFile("src/Util.java", "class Util {}"),
                new ProblemFile("README.md", "# 안내")
        )));

        Problem found = problemRepository.findById(saved.id()).orElseThrow();

        assertThat(found.files()).containsExactly(
                new ProblemFile("src/Main.java", "class Main {}"),
                new ProblemFile("src/Util.java", "class Util {}"),
                new ProblemFile("README.md", "# 안내")
        );
    }

    @Test
    void 모든_문제를_ID_순으로_반환한다() {
        Problem first = problemRepository.save(newProblem("첫 번째"));
        Problem second = problemRepository.save(newProblem("두 번째"));
        Problem third = problemRepository.save(newProblem("세 번째"));

        assertThat(problemRepository.findAll())
                .extracting(Problem::id)
                .containsExactly(first.id(), second.id(), third.id());
    }

    @Test
    void 없는_ID면_빈_Optional을_반환한다() {
        assertThat(problemRepository.findById(999L)).isEmpty();
    }

    private Problem newProblem(String title) {
        return new Problem(null, title, "# " + title, List.of(
                new ProblemFile("src/Main.java", "class Main {}")
        ));
    }
}
