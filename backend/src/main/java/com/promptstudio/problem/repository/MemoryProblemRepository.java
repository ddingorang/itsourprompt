package com.promptstudio.problem.repository;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemoryProblemRepository implements ProblemRepository {

    private static final String MAIN_JAVA_SKELETON = """
            public class Main {
                public static void main(String[] args) {
                    // 여기에 코드를 작성하세요.
                }
            }
            """;

    private final List<Problem> problems = List.of(
            new Problem(
                    1L,
                    "Hello World 출력",
                    "# Hello World 출력\n\n표준 출력으로 `Hello, World!`를 출력하세요.",
                    List.of(new ProblemFile("src/main/java/Main.java", MAIN_JAVA_SKELETON))
            ),
            new Problem(
                    2L,
                    "SSAFY 출력",
                    "# SSAFY 출력\n\n표준 출력으로 `SSAFY`를 출력하세요.",
                    List.of(new ProblemFile("src/main/java/Main.java", MAIN_JAVA_SKELETON))
            ),
            new Problem(
                    3L,
                    "환영 메시지 출력",
                    "# 환영 메시지 출력\n\n표준 출력으로 `프롬프트 스튜디오에 오신 것을 환영합니다!`를 출력하세요.",
                    List.of(new ProblemFile("src/main/java/Main.java", MAIN_JAVA_SKELETON))
            )
    );

    @Override
    public List<Problem> findAll() {
        return problems;
    }

    @Override
    public Optional<Problem> findById(Long id) {
        for (Problem problem : problems) {
            if (problem.id().equals(id)) {
                return Optional.of(problem);
            }
        }

        return Optional.empty();
    }
}
