package com.promptstudio.support;

import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.domain.PromptScopeDecision;
import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.attempt.port.CodeGenerator;
import com.promptstudio.attempt.port.FeedbackGenerator;
import com.promptstudio.attempt.port.PromptScopeValidator;
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

    /**
     * 단가표(test-model: input 1.0 / cached-input 0.5 / output 2.0 per 1M)로 손계산이 되는 값이다.
     * 라운드별 비용은 0.00120000과 0.00180000, 턴 합계는 0.00300000이다.
     */
    public static final List<LlmCallUsage> CODE_GENERATION_USAGE = List.of(
            new LlmCallUsage(1, "test-model", 1_000L, 200L, 400L, 50L, 120L),
            new LlmCallUsage(2, "test-model", 1_500L, 300L, 600L, 70L, 140L)
    );

    /**
     * 비용 0.00280000. 피드백 호출은 턴에 속하지 않아 전체 총계에만 잡힌다.
     */
    public static final List<LlmCallUsage> FEEDBACK_USAGE = List.of(
            new LlmCallUsage(1, "test-model", 2_000L, 400L, 0L, 100L, 200L)
    );

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

    @Bean
    @Primary
    public PromptScopeValidator promptScopeValidator() {
        return (problem, userPrompt) -> new PromptScopeDecision(PromptScopeDecision.Status.ALLOW, "");
    }

    public static class FakeCodeGenerator implements CodeGenerator {

        private static final GeneratedCode DEFAULT_RESULT = new GeneratedCode(
                List.of(new ProblemFile("src/main/java/Main.java", "생성된 내용")),
                "생성 요약",
                List.of(new ToolCallEntry("edit_file", "src/main/java/Main.java")),
                CODE_GENERATION_USAGE
        );

        private final AtomicInteger invocationCount = new AtomicInteger();

        private ProblemView receivedProblem;
        private AttemptView receivedAttempt;
        private String receivedPrompt;
        private RuntimeException nextFailure;
        private GeneratedCode nextResult = DEFAULT_RESULT;
        private CountDownLatch nextEntered;
        private CountDownLatch nextGate;

        @Override
        public GeneratedCode generate(ProblemView problem, AttemptView attempt, String userPrompt) {
            invocationCount.incrementAndGet();
            this.receivedProblem = problem;
            this.receivedAttempt = attempt;
            this.receivedPrompt = userPrompt;

            block();

            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;

                throw failure;
            }

            return nextResult;
        }

        /**
         * 다음 호출 한 번만 실패시킨다.
         */
        public void failNextWith(RuntimeException failure) {
            this.nextFailure = failure;
        }

        /** 다음 코드 생성 한 번을 gate가 열릴 때까지 멈춘다. */
        public void blockNextWith(CountDownLatch entered, CountDownLatch gate) {
            this.nextEntered = entered;
            this.nextGate = gate;
        }

        private void block() {
            CountDownLatch entered = nextEntered;
            CountDownLatch gate = nextGate;
            nextEntered = null;
            nextGate = null;

            if (entered == null || gate == null) {
                return;
            }

            entered.countDown();
            try {
                gate.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Fake code generation was interrupted", exception);
            }
        }

        /**
         * 다음 호출부터 이 결과를 반환한다. reset() 전까지 유지된다.
         */
        public void respondWith(GeneratedCode result) {
            this.nextResult = result;
        }

        /**
         * src/main/java/Main.java의 내용을 given content로 갈아끼운 생성 결과를 만든다.
         */
        public static GeneratedCode generating(String mainJavaContent) {
            return new GeneratedCode(
                    List.of(new ProblemFile("src/main/java/Main.java", mainJavaContent)),
                    "생성 요약",
                    List.of(new ToolCallEntry("edit_file", "src/main/java/Main.java")),
                    CODE_GENERATION_USAGE
            );
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
            nextResult = DEFAULT_RESULT;
            nextEntered = null;
            nextGate = null;
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

            return new AttemptFeedback(turnFeedbacks, "생성된 피드백", FEEDBACK_USAGE);
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
