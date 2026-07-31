package com.promptstudio.attempt.port;

public class FeedbackTimeoutException extends RuntimeException {

    public FeedbackTimeoutException() {
        super("AI feedback request timed out.");
    }
}
