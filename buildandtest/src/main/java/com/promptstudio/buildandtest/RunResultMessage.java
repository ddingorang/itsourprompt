package com.promptstudio.buildandtest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * run.result 와이어 포맷. 백엔드의 {@code com.promptstudio.rabbit.RunResultMessage}와 같은 형태다.
 *
 * <p>{@link RunRequestMessage}와 같은 계약 진화 규칙을 따른다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunResultMessage(
        UUID runId,
        String status,
        Integer exitCode,
        String stdout,
        String stderr,
        Long durationMs
) {
}
