package com.promptstudio.rabbit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * run.result 와이어 포맷. buildandtest 워커에 같은 record가 중복 정의되어 있다.
 *
 * <p>{@link RunRequestMessage}와 같은 계약 진화 규칙을 따른다.
 *
 * @param cases 채점 테스트의 케이스별 결과. 나중에 추가된 옵셔널 필드다. 케이스 기록 이전 버전의
 *              워커가 보낸 메시지에는 없어서 null이고, 그때도 {@code status}만으로 판정이 성립한다.
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

    /**
     * 케이스 기록 이전 버전의 워커가 보내던 형태. cases가 없는 메시지를 뜻하며,
     * 그 상황을 코드로 표현할 때 쓴다(주로 테스트).
     */
    public RunResultMessage(
            UUID runId,
            String status,
            Integer exitCode,
            String stdout,
            String stderr,
            Long durationMs
    ) {
        this(runId, status, exitCode, stdout, stderr, durationMs, null);
    }

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
