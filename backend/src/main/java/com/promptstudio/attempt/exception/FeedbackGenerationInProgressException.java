package com.promptstudio.attempt.exception;

public class FeedbackGenerationInProgressException extends RuntimeException {

    public FeedbackGenerationInProgressException(Long attemptId) {
        super("어템프트 ID " + attemptId + "는 피드백 생성이 진행 중입니다.");
    }
}
