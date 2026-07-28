package com.promptstudio.support;

import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.port.CodeGenerator;
import com.promptstudio.attempt.port.FeedbackGenerator;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

@TestConfiguration
public class FakeAiConfiguration {

    @Bean
    @Primary
    public FakeCodeGenerator fakeCodeGenerator() {
        return new FakeCodeGenerator();
    }

    @Bean
    @Primary
    public FakeFeedbackGenerator fakeFeedbackGenerator() {
        return new FakeFeedbackGenerator();
    }

    public static class FakeCodeGenerator implements CodeGenerator {

        private final GeneratedCode result = new GeneratedCode(
                List.of(new ProblemFile("src/main/java/Main.java", "생성된 내용")),
                "생성 요약"
        );

        private AttemptView receivedAttempt;
        private String receivedPrompt;

        @Override
        public GeneratedCode generate(AttemptView attempt, String userPrompt) {
            this.receivedAttempt = attempt;
            this.receivedPrompt = userPrompt;

            return result;
        }

        public AttemptView receivedAttempt() {
            return receivedAttempt;
        }

        public String receivedPrompt() {
            return receivedPrompt;
        }
    }

    public static class FakeFeedbackGenerator implements FeedbackGenerator {

        private ProblemView receivedProblem;
        private AttemptView receivedAttempt;

        @Override
        public String generate(ProblemView problem, AttemptView attempt) {
            this.receivedProblem = problem;
            this.receivedAttempt = attempt;

            return "생성된 피드백";
        }

        public ProblemView receivedProblem() {
            return receivedProblem;
        }

        public AttemptView receivedAttempt() {
            return receivedAttempt;
        }
    }
}
