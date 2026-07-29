package com.promptstudio.attempt.exception;

public class AttemptAlreadySubmittedException extends RuntimeException {

    public AttemptAlreadySubmittedException(Long attemptId) {
        super("어템프트 ID " + attemptId + "는 이미 제출되었습니다.");
    }
}
