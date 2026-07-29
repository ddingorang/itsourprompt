package com.promptstudio.support;

import com.promptstudio.problem.port.ProblemSourceClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@TestConfiguration
public class FakeProblemSourceConfiguration {

    @Bean
    @Primary
    public FakeProblemSourceClient fakeProblemSourceClient() {
        return new FakeProblemSourceClient();
    }

    public static class FakeProblemSourceClient implements ProblemSourceClient {

        private final AtomicInteger downloadCount = new AtomicInteger();

        private String sha;
        private byte[] archiveZip;
        private String requestedSha;
        private RuntimeException nextFailure;

        @Override
        public Optional<String> latestCommitSha() {
            return Optional.ofNullable(sha);
        }

        @Override
        public byte[] downloadArchiveZip(String sha) {
            downloadCount.incrementAndGet();
            this.requestedSha = sha;

            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;

                throw failure;
            }

            return archiveZip;
        }

        /**
         * 저장소가 이 커밋과 이 archive를 내려주도록 만든다.
         */
        public void serve(String sha, byte[] archiveZip) {
            this.sha = sha;
            this.archiveZip = archiveZip;
        }

        /**
         * 다음 호출 한 번만 실패시킨다.
         */
        public void failNextWith(RuntimeException failure) {
            this.nextFailure = failure;
        }

        public int downloadCount() {
            return downloadCount.get();
        }

        public String requestedSha() {
            return requestedSha;
        }

        /**
         * 컨텍스트 캐시로 빈이 테스트끼리 공유되므로, 호출 횟수를 보는 테스트는 시작 전에 비워야 한다.
         */
        public void reset() {
            downloadCount.set(0);
            sha = null;
            archiveZip = null;
            requestedSha = null;
            nextFailure = null;
        }
    }
}
