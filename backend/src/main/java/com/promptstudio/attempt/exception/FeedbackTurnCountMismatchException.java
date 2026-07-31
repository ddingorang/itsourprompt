package com.promptstudio.attempt.exception;

public class FeedbackTurnCountMismatchException extends RuntimeException {

    public FeedbackTurnCountMismatchException(int turnCount, int feedbackCount) {
        super("어템프트의 턴 수(" + turnCount + ")와 턴 피드백 개수(" + feedbackCount + ")가 다릅니다.");
    }
}
