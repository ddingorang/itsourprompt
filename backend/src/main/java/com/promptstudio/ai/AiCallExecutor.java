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

    /**
     * 태스크마다 스레드를 새로 만드는 executor여야 한다. {@link #submit}으로 띄운 작업이 그 안에서 다시
     * {@link #call}을 부르는 2단 구조이기 때문이다 — 고정 크기 풀로 바꾸면 바깥 작업 둘이 풀을 채우고
     * 안쪽 호출 둘이 큐에서 굶어 데드락이 된다.
     */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 상한 없이 띄우기만 한다. 상한은 호출 안쪽의 {@link #call}이 이미 갖고 있어, 바깥에서 또 재면
     * 자기 예산 안에서 정상 진행 중인 호출을 자르게 된다.
     */
    public <T> Future<T> submit(Callable<T> aiCall) {
        // MDC는 ThreadLocal이라 스레드가 갈리는 순간 requestId가 끊긴다. 추적이 제일 필요한
        // LLM 경로가 정확히 여기라, 호출자 컨텍스트를 복사해 작업 스레드에 다시 심는다.
        Map<String, String> callerContext = MDC.getCopyOfContextMap();

        return executor.submit(() -> {
            if (callerContext != null) {
                MDC.setContextMap(callerContext);
            }

            try {
                return aiCall.call();
            } finally {
                MDC.clear();
            }
        });
    }

    public <T> T call(Callable<T> aiCall, long timeoutMinutes)
            throws TimeoutException, InterruptedException, ExecutionException {
        Future<T> future = submit(aiCall);

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
