package com.promptstudio.attempt.exception;

public class AttemptNotFoundException extends RuntimeException {

    public AttemptNotFoundException(Long attemptId) {
        super("어템프트 ID " + attemptId + "를 찾을 수 없습니다.");
    }
}
