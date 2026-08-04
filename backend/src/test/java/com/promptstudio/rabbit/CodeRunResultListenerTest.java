package com.promptstudio.rabbit;

import com.promptstudio.attempt.domain.CodeRunCase;
import com.promptstudio.attempt.domain.CodeRunCaseStatus;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.service.CodeRunService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 브로커 없이 리스너의 번역 로직만 검증한다. 통합 컨텍스트에서는 이 빈을 만들지 않으므로
 * (테스트 application.yml의 result-listener-enabled=false) 여기서 직접 확인한다.
 */
class CodeRunResultListenerTest {

    private static final UUID RUN_ID = UUID.randomUUID();

    private final CodeRunService codeRunService = mock(CodeRunService.class);
    private final CodeRunResultListener listener = new CodeRunResultListener(codeRunService);

    @AfterEach
    void MDC를_비운다() {
        MDC.clear();
    }

    @Test
    void 정상_결과는_그대로_전달된다() {
        listener.onResult(new RunResultMessage(RUN_ID, "SUCCEEDED", 0, "출력", "", 1840L));

        CodeRunResult applied = captureApplied();
        assertThat(applied.runId()).isEqualTo(RUN_ID);
        assertThat(applied.status()).isEqualTo(CodeRunStatus.SUCCEEDED);
        assertThat(applied.exitCode()).isZero();
        assertThat(applied.stdout()).isEqualTo("출력");
        assertThat(applied.durationMs()).isEqualTo(1840L);
    }

    @Test
    void 컴파일_오류_상태도_전달된다() {
        listener.onResult(new RunResultMessage(RUN_ID, "COMPILE_ERROR", 1, "", "error: ';' expected", 420L));

        assertThat(captureApplied().status()).isEqualTo(CodeRunStatus.COMPILE_ERROR);
    }

    @Test
    void 모르는_상태값은_RUNNER_ERROR로_낮춘다() {
        listener.onResult(new RunResultMessage(RUN_ID, "SOME_FUTURE_STATUS", null, "", "", 1L));

        assertThat(captureApplied().status()).isEqualTo(CodeRunStatus.RUNNER_ERROR);
    }

    @Test
    void 상태값이_null이면_RUNNER_ERROR로_낮춘다() {
        listener.onResult(new RunResultMessage(RUN_ID, null, null, "", "", 1L));

        assertThat(captureApplied().status()).isEqualTo(CodeRunStatus.RUNNER_ERROR);
    }

    @Test
    void 워커가_QUEUED를_보내면_RUNNER_ERROR로_낮춘다() {
        listener.onResult(new RunResultMessage(RUN_ID, "QUEUED", null, "", "", 1L));

        assertThat(captureApplied().status()).isEqualTo(CodeRunStatus.RUNNER_ERROR);
    }

    @Test
    void 케이스가_있으면_상태를_변환해_전달한다() {
        listener.onResult(new RunResultMessage(RUN_ID, "TEST_FAILED", 1, "", "", 1200L, List.of(
                new RunResultMessage.RunCaseMessage("T", "통과()", "PASSED", null, 30L),
                new RunResultMessage.RunCaseMessage("T", "실패()", "FAILED", "expected: <1> but was: <2>", 17L),
                new RunResultMessage.RunCaseMessage("T", "죽음()", "ERROR", "boom", 1L),
                new RunResultMessage.RunCaseMessage("T", "건너뜀()", "SKIPPED", null, null))));

        assertThat(captureApplied().cases())
                .extracting(CodeRunCase::status)
                .containsExactly(CodeRunCaseStatus.PASSED, CodeRunCaseStatus.FAILED,
                        CodeRunCaseStatus.ERROR, CodeRunCaseStatus.SKIPPED);
    }

    /** 케이스 기록 이전 버전의 워커가 보낸 메시지다. */
    @Test
    void cases가_null이면_빈_목록으로_전달한다() {
        listener.onResult(new RunResultMessage(RUN_ID, "SUCCEEDED", 0, "", "", 1L));

        assertThat(captureApplied().cases()).isEmpty();
    }

    /**
     * 케이스는 보조 자료다. 알 수 없는 상태값을 임의로 뭉개면 통과/실패가 뒤집히므로 그 케이스만 버린다.
     * 실행 판정(status)은 건드리지 않는다.
     */
    @Test
    void 모르는_케이스_상태값은_그_케이스만_버린다() {
        listener.onResult(new RunResultMessage(RUN_ID, "TEST_FAILED", 1, "", "", 1L, List.of(
                new RunResultMessage.RunCaseMessage("T", "정상()", "PASSED", null, 1L),
                new RunResultMessage.RunCaseMessage("T", "미래상태()", "SOME_FUTURE_CASE_STATUS", null, 1L),
                new RunResultMessage.RunCaseMessage("T", "상태없음()", null, null, 1L))));

        CodeRunResult applied = captureApplied();
        assertThat(applied.status()).isEqualTo(CodeRunStatus.TEST_FAILED);
        assertThat(applied.cases()).extracting(CodeRunCase::name).containsExactly("정상()");
    }

    @Test
    void 이름_없는_케이스는_버린다() {
        listener.onResult(new RunResultMessage(RUN_ID, "SUCCEEDED", 0, "", "", 1L, List.of(
                new RunResultMessage.RunCaseMessage("T", null, "PASSED", null, 1L),
                new RunResultMessage.RunCaseMessage("T", "  ", "PASSED", null, 1L))));

        assertThat(captureApplied().cases()).isEmpty();
    }

    @Test
    void runId가_없으면_아무것도_하지_않는다() {
        listener.onResult(new RunResultMessage(null, "SUCCEEDED", 0, "", "", 1L));

        verify(codeRunService, never()).applyResult(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 처리하는_동안_MDC에_runId가_실린다() {
        AtomicReference<String> duringHandling = new AtomicReference<>();
        doAnswer(invocation -> {
            duringHandling.set(MDC.get(CodeRunResultListener.MDC_RUN_ID));

            return null;
        }).when(codeRunService).applyResult(org.mockito.ArgumentMatchers.any());

        listener.onResult(new RunResultMessage(RUN_ID, "SUCCEEDED", 0, "", "", 1L));

        assertThat(duringHandling.get()).isEqualTo(RUN_ID.toString());
    }

    @Test
    void 처리가_끝나면_MDC에_runId가_남지_않는다() {
        // 리스너 컨테이너 스레드는 풀에서 재사용되므로 남으면 다음 메시지에 이전 runId가 붙는다.
        listener.onResult(new RunResultMessage(RUN_ID, "SUCCEEDED", 0, "", "", 1L));

        assertThat(MDC.get(CodeRunResultListener.MDC_RUN_ID)).isNull();
    }

    private CodeRunResult captureApplied() {
        ArgumentCaptor<CodeRunResult> captor = ArgumentCaptor.forClass(CodeRunResult.class);
        verify(codeRunService).applyResult(captor.capture());

        return captor.getValue();
    }
}
