package com.promptstudio.attempt.domain;

public record FileChange(
        String path,
        ChangeType type
) {

    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED
    }
}
