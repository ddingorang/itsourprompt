package com.promptstudio.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 호출은 별도 스레드에서 돈다. MDC가 ThreadLocal이라 그대로 두면 그 스레드가 남기는 로그에
 * requestId가 비어 나가 요청과 LLM 호출이 끊긴다.
 */
class AiCallExecutorTest {

    private final AiCallExecutor executor = new AiCallExecutor();

    @AfterEach
    void 정리한다() {
        MDC.clear();
        executor.shutdown();
    }

    @Test
    void 작업_스레드에_호출자의_MDC가_전파된다() throws Exception {
        MDC.put("requestId", "req-1");
        AtomicReference<Map<String, String>> workerContext = new AtomicReference<>();

        executor.call(() -> {
            workerContext.set(MDC.getCopyOfContextMap());

            return "ok";
        }, 1);

        assertThat(workerContext.get()).containsEntry("requestId", "req-1");
    }

    @Test
    void 호출_후에도_호출자의_MDC는_그대로다() throws Exception {
        MDC.put("requestId", "req-2");

        executor.call(() -> "ok", 1);

        assertThat(MDC.get("requestId")).isEqualTo("req-2");
    }

    @Test
    void 호출자에_MDC가_없어도_동작한다() throws Exception {
        assertThat(executor.call(() -> "ok", 1)).isEqualTo("ok");
    }
}
