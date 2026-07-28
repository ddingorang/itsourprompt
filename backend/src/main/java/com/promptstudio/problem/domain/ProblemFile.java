package com.promptstudio.problem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ProblemFile(

        @Column(nullable = false)
        String path,

        @Column(nullable = false, columnDefinition = "text")
        String content
) {
}
