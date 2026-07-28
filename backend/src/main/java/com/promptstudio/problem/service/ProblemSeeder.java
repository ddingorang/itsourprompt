package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProblemSeeder implements ApplicationRunner {

    private static final String MAIN_JAVA_SKELETON = """
            public class Main {
                public static void main(String[] args) {
                    // 여기에 코드를 작성하세요.
                }
            }
            """;

    // 엔티티는 저장되면 id가 박히므로 상수로 공유하지 않는다. 호출마다 새로 만든다.
    private static List<Problem> seedProblems() {
        return List.of(
                new Problem(
                        "Hello World 출력",
                        "# Hello World 출력\n\n표준 출력으로 `Hello, World!`를 출력하세요.",
                        List.of(new ProblemFile("src/main/java/Main.java", MAIN_JAVA_SKELETON))
                ),
                new Problem(
                        "SSAFY 출력",
                        "# SSAFY 출력\n\n표준 출력으로 `SSAFY`를 출력하세요.",
                        List.of(new ProblemFile("src/main/java/Main.java", MAIN_JAVA_SKELETON))
                ),
                new Problem(
                        "환영 메시지 출력",
                        "# 환영 메시지 출력\n\n표준 출력으로 `프롬프트 스튜디오에 오신 것을 환영합니다!`를 출력하세요.",
                        List.of(new ProblemFile("src/main/java/Main.java", MAIN_JAVA_SKELETON))
                )
        );
    }

    private final ProblemRepository problemRepository;

    public ProblemSeeder(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        if (!problemRepository.findAll().isEmpty()) {
            return;
        }

        for (Problem problem : seedProblems()) {
            problemRepository.save(problem);
        }
    }
}
