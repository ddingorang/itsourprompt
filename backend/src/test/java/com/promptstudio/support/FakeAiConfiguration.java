package com.promptstudio.support;

import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.attempt.port.CodeGenerator;
import com.promptstudio.attempt.port.FeedbackGenerator;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
                "생성 요약",
                List.of(new ToolCallEntry("edit_file", "src/main/java/Main.java"))
        );

        private final AtomicInteger invocationCount = new AtomicInteger();

        private ProblemView receivedProblem;
        private AttemptView receivedAttempt;
        private String receivedPrompt;
        private RuntimeException nextFailure;

        @Override
        public GeneratedCode generate(ProblemView problem, AttemptView attempt, String userPrompt) {
            invocationCount.incrementAndGet();
            this.receivedProblem = problem;
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

        public ProblemView receivedProblem() {
            return receivedProblem;
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
            receivedProblem = null;
            receivedAttempt = null;
            receivedPrompt = null;
            nextFailure = null;
        }
    }

    public static class FakeFeedbackGenerator implements FeedbackGenerator {

        private final AtomicInteger invocationCount = new AtomicInteger();

        private ProblemView receivedProblem;
        private AttemptView receivedAttempt;
        private RuntimeException nextFailure;
        private CountDownLatch nextEntered;
        private CountDownLatch nextGate;

        @Override
        public AttemptFeedback generate(ProblemView problem, AttemptView attempt) {
            invocationCount.incrementAndGet();
            this.receivedProblem = problem;
            this.receivedAttempt = attempt;

            block();

            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;

                throw failure;
            }

            List<String> turnFeedbacks = new ArrayList<>();

            for (int index = 0; index < attempt.turns().size(); index++) {
                turnFeedbacks.add("턴 " + (index + 1) + " 피드백");
            }

            return new AttemptFeedback(turnFeedbacks, "생성된 피드백");
        }

        /**
         * 다음 호출 한 번만 실패시킨다.
         */
        public void failNextWith(RuntimeException failure) {
            this.nextFailure = failure;
        }

        /**
         * 다음 호출 한 번만 gate가 열릴 때까지 멈춘다. 생성 진행 중 상태를 테스트에서 관찰하기 위한 훅이다.
         */
        public void blockNextWith(CountDownLatch entered, CountDownLatch gate) {
            this.nextEntered = entered;
            this.nextGate = gate;
        }

        private void block() {
            if (nextGate == null) {
                return;
            }

            CountDownLatch entered = nextEntered;
            CountDownLatch gate = nextGate;
            nextEntered = null;
            nextGate = null;

            entered.countDown();

            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                throw new RuntimeException(e);
            }
        }

        public ProblemView receivedProblem() {
            return receivedProblem;
        }

        public AttemptView receivedAttempt() {
            return receivedAttempt;
        }

        public int invocationCount() {
            return invocationCount.get();
        }

        /**
         * 컨텍스트 캐시로 빈이 테스트끼리 공유되므로, 호출 횟수를 보는 테스트는 시작 전에 비워야 한다.
         */
        public void reset() {
            invocationCount.set(0);
            receivedProblem = null;
            receivedAttempt = null;
            nextFailure = null;
            nextEntered = null;
            nextGate = null;
        }
    }
}
