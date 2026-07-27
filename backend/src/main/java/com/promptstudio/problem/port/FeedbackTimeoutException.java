package com.promptstudio.problem.port;

public class FeedbackTimeoutException extends RuntimeException {

    public FeedbackTimeoutException() {
        super("AI feedback request timed out.");
    }
}
