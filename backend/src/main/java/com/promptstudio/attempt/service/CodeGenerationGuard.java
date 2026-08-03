package com.promptstudio.attempt.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 어템프트별 AI 코드 생성 진행 여부를 메모리에서 추적한다. 단일 인스턴스 배포 전제다.
 */
@Component
class CodeGenerationGuard {

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    boolean tryAcquire(Long attemptId) {
        return inFlight.add(attemptId);
    }

    boolean isGenerating(Long attemptId) {
        return inFlight.contains(attemptId);
    }

    void release(Long attemptId) {
        inFlight.remove(attemptId);
    }
}
