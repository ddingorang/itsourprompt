package com.promptstudio.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        assertThat(message.files()).hasSize(1);
        assertThat(message.files().getFirst().path()).isEqualTo("src/main/java/Main.java");
        assertThat(message.files().getFirst().content()).contains("Hello World");
    }

    @Test
    void run_result_fixture를_모르는_필드까지_포함해_읽는다() throws IOException {
        RunResultMessage message = read("contract/run-result.json", RunResultMessage.class);

        assertThat(message.runId()).isEqualTo(RUN_ID);
        assertThat(message.status()).isEqualTo("SUCCEEDED");
        assertThat(message.exitCode()).isZero();
        assertThat(message.durationMs()).isEqualTo(1840L);
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
