package com.promptstudio.problem.domain;

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
