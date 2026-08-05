package com.promptstudio.attempt.domain;

public record PromptScopeDecision(Status status, String message) {

    public enum Status {
        ALLOW,
        OUT_OF_SCOPE,
        INSUFFICIENT
    }

    public boolean isRejected() {
        return status != Status.ALLOW;
    }
}
