package com.promptstudio.ai;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

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

    public String call(Callable<String> aiCall, long timeoutMinutes)
            throws TimeoutException, InterruptedException, ExecutionException {
        Future<String> future = executor.submit(aiCall);

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
