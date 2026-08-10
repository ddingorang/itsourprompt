package com.promptstudio.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptstudio.attempt.domain.CodeRunCaseStatus;
import com.promptstudio.attempt.domain.CodeRunStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * buildandtest 워커와 공유하는 계약 fixture로 역직렬화를 검증한다.
 *
 * <p>같은 파일이 buildandtest/src/test/resources/contract/ 에도 있고 두 사본은 항상 동일해야 한다.
 * 한쪽만 필드를 바꾸면 다른 쪽 테스트가 깨져서 드리프트가 드러난다.
 */
class RunMessageContractTest {

    private static final UUID RUN_ID = UUID.fromString("3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void run_request_fixture를_모르는_필드까지_포함해_읽는다() throws IOException {
        RunRequestMessage message = read("contract/run-request.json", RunRequestMessage.class);

        assertThat(message.runId()).isEqualTo(RUN_ID);
        assertThat(message.attemptId()).isEqualTo(7L);
        assertThat(message.language()).isEqualTo("java");
        assertThat(message.files()).hasSize(1);
        assertThat(message.files().getFirst().path()).isEqualTo("src/main/java/Main.java");
        assertThat(message.files().getFirst().content()).contains("Hello World");
        assertThat(message.testFiles()).hasSize(1);
        assertThat(message.testFiles().getFirst().path()).isEqualTo("src/test/java/MainTest.java");
        assertThat(message.testFiles().getFirst().content()).contains("org.junit.jupiter.api.Test");
    }

    /**
     * 워커가 먼저 배포되어 testFiles를 아직 안 보내는 백엔드의 메시지를 받는 상황.
     */
    @Test
    void testFiles가_없는_옛_메시지도_읽는다() throws IOException {
        RunRequestMessage message = objectMapper.readValue("""
                {"runId":"3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071","attemptId":7,"files":[]}
                """, RunRequestMessage.class);

        assertThat(message.testFiles()).isNull();
    }

    /** language 도입 전의 메시지는 null로 읽히고, 수신 측이 java로 해석한다. */
    @Test
    void language가_없는_옛_메시지도_읽는다() throws IOException {
        RunRequestMessage message = objectMapper.readValue("""
                {"runId":"3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071","attemptId":7,"files":[]}
                """, RunRequestMessage.class);

        assertThat(message.language()).isNull();
    }

    @Test
    void run_result_fixture를_모르는_필드까지_포함해_읽는다() throws IOException {
        RunResultMessage message = read("contract/run-result.json", RunResultMessage.class);

        assertThat(message.runId()).isEqualTo(RUN_ID);
        assertThat(message.status()).isEqualTo("SUCCEEDED");
        assertThat(message.exitCode()).isZero();
        assertThat(message.durationMs()).isEqualTo(1840L);
        assertThat(message.cases()).hasSize(2);
        assertThat(message.cases().getFirst().className()).isEqualTo("MainTest");
        assertThat(message.cases().getFirst().name()).isEqualTo("실행된다()");
        assertThat(message.cases().getFirst().status()).isEqualTo("PASSED");
        assertThat(message.cases().getFirst().durationMs()).isEqualTo(30L);
    }

    /**
     * fixture의 케이스 상태 문자열이 백엔드 enum으로 그대로 변환되어야 한다.
     */
    @Test
    void fixture의_케이스_상태값이_도메인_enum과_일치한다() throws IOException {
        RunResultMessage message = read("contract/run-result.json", RunResultMessage.class);

        for (RunResultMessage.RunCaseMessage source : message.cases()) {
            assertThat(CodeRunCaseStatus.valueOf(source.status())).isNotNull();
        }
    }

    /** 케이스 기록 이전 버전의 워커가 보낸 메시지도 읽혀야 한다. */
    @Test
    void cases가_없는_옛_메시지도_읽는다() throws IOException {
        RunResultMessage message = objectMapper.readValue("""
                {"runId":"3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071","status":"SUCCEEDED","exitCode":0,"durationMs":10}
                """, RunResultMessage.class);

        assertThat(message.cases()).isNull();
    }

    /**
     * fixture의 status 문자열이 백엔드 enum으로 그대로 변환되어야 한다 — 계약의 실질적인 접점이다.
     */
    @Test
    void fixture의_상태값은_백엔드_enum으로_변환된다() throws IOException {
        RunResultMessage message = read("contract/run-result.json", RunResultMessage.class);

        assertThat(CodeRunStatus.valueOf(message.status())).isEqualTo(CodeRunStatus.SUCCEEDED);
    }

    private <T> T read(String resource, Class<T> type) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("fixture %s", resource).isNotNull();

            return objectMapper.readValue(in, type);
        }
    }
}
