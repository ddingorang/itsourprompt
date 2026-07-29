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
import java.util.concurrent.atomic.AtomicInteger;

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

        private final AtomicInteger invocationCount = new AtomicInteger();

        private AttemptView receivedAttempt;
        private String receivedPrompt;
        private RuntimeException nextFailure;

        @Override
        public GeneratedCode generate(AttemptView attempt, String userPrompt) {
            invocationCount.incrementAndGet();
            this.receivedAttempt = attempt;
            this.receivedPrompt = userPrompt;

            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;

                throw failure;
            }

            return result;
        }

        /**
         * 다음 호출 한 번만 실패시킨다.
         */
        public void failNextWith(RuntimeException failure) {
            this.nextFailure = failure;
        }

        public AttemptView receivedAttempt() {
            return receivedAttempt;
        }

        public String receivedPrompt() {
            return receivedPrompt;
        }

        public int invocationCount() {
            return invocationCount.get();
        }

        /**
         * 컨텍스트 캐시로 빈이 테스트끼리 공유되므로, 호출 횟수를 보는 테스트는 시작 전에 비워야 한다.
         */
        public void reset() {
            invocationCount.set(0);
            receivedAttempt = null;
            receivedPrompt = null;
            nextFailure = null;
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
