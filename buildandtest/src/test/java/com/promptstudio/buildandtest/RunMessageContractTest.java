package com.promptstudio.buildandtest;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 백엔드와 공유하는 계약 fixture로 역직렬화를 검증한다.
 *
 * <p>같은 파일이 backend/src/test/resources/contract/ 에도 있고 두 사본은 항상 동일해야 한다.
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
     * 백엔드가 아직 testFiles를 안 보내는 시점에도 워커가 죽지 않아야 한다.
     */
    @Test
    void testFiles가_없는_옛_메시지도_읽는다() throws IOException {
        RunRequestMessage message = new ObjectMapper().readValue("""
                {"runId":"3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071","attemptId":7,"files":[]}
                """, RunRequestMessage.class);

        assertThat(message.testFiles()).isNull();
    }

    /** language 도입 전의 백엔드가 보낸 메시지는 null로 읽히고, 워커는 java로 해석한다. */
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
     * 백엔드가 아직 cases를 모르는 시점에도, 반대로 옛 워커가 cases 없이 보낸 메시지도 읽혀야 한다.
     */
    @Test
    void cases가_없는_옛_메시지도_읽는다() throws IOException {
        RunResultMessage message = objectMapper.readValue("""
                {"runId":"3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071","status":"SUCCEEDED","exitCode":0,"durationMs":10}
                """, RunResultMessage.class);

        assertThat(message.cases()).isNull();
    }

    @Test
    void 실패한_케이스는_사유를_싣는다() throws IOException {
        RunResultMessage message = objectMapper.readValue("""
                {"runId":"3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071","status":"TEST_FAILED","exitCode":1,
                 "cases":[{"className":"MainTest","name":"출력을_검사한다()","status":"FAILED",
                           "message":"expected: <Hello> but was: <Hi>","durationMs":17}]}
                """, RunResultMessage.class);

        assertThat(message.cases()).singleElement().satisfies(testCase -> {
            assertThat(testCase.status()).isEqualTo("FAILED");
            assertThat(testCase.message()).isEqualTo("expected: <Hello> but was: <Hi>");
        });
    }

    @Test
    void 워커가_내는_모든_케이스_상태값은_계약_문자열로_직렬화된다() {
        for (RunCaseStatus status : RunCaseStatus.values()) {
            assertThat(status.name()).isNotBlank();
        }
    }

    @Test
    void 워커가_내는_모든_상태값은_계약_문자열로_직렬화된다() {
        for (RunStatus status : RunStatus.values()) {
            assertThat(status.name()).isNotBlank();
        }
    }

    private <T> T read(String resource, Class<T> type) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("fixture %s", resource).isNotNull();

            return objectMapper.readValue(in, type);
        }
    }
}
