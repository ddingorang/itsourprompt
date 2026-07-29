package com.promptstudio.problem.port;

public class ProblemSourceException extends RuntimeException {

    public ProblemSourceException(String message) {
        super(message);
    }

    public ProblemSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
