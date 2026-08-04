package com.promptstudio.attempt.exception;

import java.util.UUID;

public class CodeRunNotFoundException extends RuntimeException {

    public CodeRunNotFoundException(Long attemptId, UUID runId) {
        super("어템프트 ID " + attemptId + "에서 실행 ID " + runId + "를 찾을 수 없습니다.");
    }
}
