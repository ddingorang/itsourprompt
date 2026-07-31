package com.promptstudio.attempt.port;

public class FeedbackGenerationException extends RuntimeException {

    public FeedbackGenerationException(String message) {
        super(message);
    }

    public FeedbackGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
