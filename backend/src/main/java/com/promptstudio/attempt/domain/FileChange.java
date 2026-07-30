package com.promptstudio.attempt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * @param content 변경 후 파일 전체 내용. DELETED는 남은 내용이 없으므로 null이다.
 */
@Embeddable
public record FileChange(

        @Column(nullable = false)
        String path,

        @Enumerated(EnumType.STRING)
        @Column(name = "change_type", nullable = false, length = 20)
        ChangeType type,

        @Column(columnDefinition = "text")
        String content
) {

    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED
    }
}
