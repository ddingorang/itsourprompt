package com.promptstudio.support;

import com.promptstudio.attempt.port.CodeRunPublisher;
import com.promptstudio.problem.domain.ProblemFile;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@TestConfiguration
public class FakeCodeRunConfiguration {

    @Bean
    @Primary
    public FakeCodeRunPublisher fakeCodeRunPublisher() {
        return new FakeCodeRunPublisher();
    }

    public static class FakeCodeRunPublisher implements CodeRunPublisher {

        private final AtomicInteger invocationCount = new AtomicInteger();
        private final List<UUID> publishedRunIds = new ArrayList<>();

        private Long receivedAttemptId;
        private String receivedLanguage;
        private List<ProblemFile> receivedFiles;
        private List<ProblemFile> receivedTestFiles;
        private RuntimeException nextFailure;

        @Override
        public void publish(UUID runId, Long attemptId, String language, List<ProblemFile> files, List<ProblemFile> testFiles) {
            invocationCount.incrementAndGet();
            this.receivedAttemptId = attemptId;
            this.receivedLanguage = language;
            this.receivedFiles = files;
            this.receivedTestFiles = testFiles;
            publishedRunIds.add(runId);

            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;

                throw failure;
            }
        }

        /**
         * 다음 호출 한 번만 실패시킨다.
         */
        public void failNextWith(RuntimeException failure) {
            this.nextFailure = failure;
        }

        public Long receivedAttemptId() {
            return receivedAttemptId;
        }

        public String receivedLanguage() {
            return receivedLanguage;
        }

        public List<ProblemFile> receivedFiles() {
            return receivedFiles;
        }

        public List<ProblemFile> receivedTestFiles() {
            return receivedTestFiles;
        }

        public List<UUID> publishedRunIds() {
            return List.copyOf(publishedRunIds);
        }

        public int invocationCount() {
            return invocationCount.get();
        }

        /**
         * 컨텍스트 캐시로 빈이 테스트끼리 공유되므로, 호출 횟수를 보는 테스트는 시작 전에 비워야 한다.
         */
        public void reset() {
            invocationCount.set(0);
            publishedRunIds.clear();
            receivedAttemptId = null;
            receivedLanguage = null;
            receivedFiles = null;
            receivedTestFiles = null;
            nextFailure = null;
        }
    }
}
