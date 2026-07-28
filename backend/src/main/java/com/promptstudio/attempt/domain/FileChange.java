package com.promptstudio.attempt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record FileChange(

        @Column(nullable = false)
        String path,

        @Enumerated(EnumType.STRING)
        @Column(name = "change_type", nullable = false, length = 20)
        ChangeType type
) {

    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED
    }
}
