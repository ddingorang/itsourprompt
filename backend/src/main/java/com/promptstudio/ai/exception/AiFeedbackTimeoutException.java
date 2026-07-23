package com.promptstudio.ai.exception;

public class AiFeedbackTimeoutException extends RuntimeException {

    public AiFeedbackTimeoutException() {
        super("AI feedback request timed out.");
    }
}
