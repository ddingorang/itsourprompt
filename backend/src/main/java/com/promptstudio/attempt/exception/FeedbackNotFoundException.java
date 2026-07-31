package com.promptstudio.attempt.exception;

public class FeedbackNotFoundException extends RuntimeException {

    public FeedbackNotFoundException(Long attemptId) {
        super("어템프트 ID " + attemptId + "의 피드백이 아직 없습니다. 제출 후 조회할 수 있습니다.");
    }
}
