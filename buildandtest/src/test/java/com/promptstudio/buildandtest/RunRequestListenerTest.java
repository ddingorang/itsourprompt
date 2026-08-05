package com.promptstudio.buildandtest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunRequestListenerTest {

    private final CodeExecutor codeExecutor = mock(CodeExecutor.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RunRequestListener listener = new RunRequestListener(codeExecutor, rabbitTemplate, "java");

    /**
     * 언어가 다른 요청은 큐 바인딩·워커 구성이 어긋난 것이다. 실행하지 않고
     * RUNNER_ERROR 결과를 발행해 조용한 오채점 대신 구성 오류로 드러낸다.
     */
    @Test
    void 워커_언어와_다른_요청은_실행하지_않고_RUNNER_ERROR를_발행한다() {
        listener.onRequest(new RunRequestMessage(UUID.randomUUID(), 7L, "python", List.of(), List.of()));

        verify(codeExecutor, never()).execute(anyList(), anyList());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RunQueues.EXCHANGE), eq(RunQueues.RESULT_ROUTING_KEY), payload.capture());

        RunResultMessage result = (RunResultMessage) payload.getValue();
        assertThat(result.status()).isEqualTo(RunStatus.RUNNER_ERROR.name());
        assertThat(result.stderr()).contains("java").contains("python");
    }

    /** 언어 필드 도입 전의 메시지는 null이며 java로 해석한다 — java 워커가 정상 처리해야 한다. */
    @Test
    void 언어가_null인_옛_메시지는_java_워커가_실행한다() {
        when(codeExecutor.execute(anyList(), any()))
                .thenReturn(RunOutcome.failure(RunStatus.SUCCEEDED, "", 1));

        listener.onRequest(new RunRequestMessage(UUID.randomUUID(), 7L, null, List.of(), List.of()));

        verify(codeExecutor).execute(anyList(), any());
    }
}
