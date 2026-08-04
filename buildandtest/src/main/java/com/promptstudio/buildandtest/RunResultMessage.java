package com.promptstudio.buildandtest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * run.result 와이어 포맷. 백엔드의 {@code com.promptstudio.rabbit.RunResultMessage}와 같은 형태다.
 *
 * <p>{@link RunRequestMessage}와 같은 계약 진화 규칙을 따른다.
 *
 * @param cases 채점 테스트의 케이스별 결과. 나중에 추가된 옵셔널 필드라 백엔드가 먼저 배포된
 *              상태에서는 비어 있고, 반대로 워커가 먼저 배포되면 백엔드가 이 필드를 무시한다.
 *              어느 쪽이든 {@code status}만으로 판정이 성립하므로 배포 순서를 맞출 필요가 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunResultMessage(
        UUID runId,
        String status,
        Integer exitCode,
        String stdout,
        String stderr,
        Long durationMs,
        List<RunCaseMessage> cases
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunCaseMessage(
            String className,
            String name,
            String status,
            String message,
            Long durationMs
    ) {
    }
}
