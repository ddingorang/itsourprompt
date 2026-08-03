package com.promptstudio.ai;

import jakarta.annotation.PreDestroy;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AiCallExecutor {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public <T> T call(Callable<T> aiCall, long timeoutMinutes)
            throws TimeoutException, InterruptedException, ExecutionException {
        // MDC는 ThreadLocal이라 스레드가 갈리는 순간 requestId가 끊긴다. 추적이 제일 필요한
        // LLM 경로가 정확히 여기라, 호출자 컨텍스트를 복사해 작업 스레드에 다시 심는다.
        Map<String, String> callerContext = MDC.getCopyOfContextMap();

        Future<T> future = executor.submit(() -> {
            if (callerContext != null) {
                MDC.setContextMap(callerContext);
            }

            try {
                return aiCall.call();
            } finally {
                MDC.clear();
            }
        });

        try {
            return future.get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
