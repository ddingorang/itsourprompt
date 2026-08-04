package com.promptstudio.rabbit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * run.request 와이어 포맷. buildandtest 워커에 같은 record가 중복 정의되어 있다.
 *
 * <p>계약 진화 규칙: 필드는 추가만 하고 이름 변경·삭제는 하지 않는다. 새 필드는 항상 옵셔널로 둔다.
 * 한쪽만 배포된 시점에도 메시지가 처리되도록 관용적 리더로 동작한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunRequestMessage(
        UUID runId,
        Long attemptId,
        List<RunFileMessage> files
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunFileMessage(String path, String content) {
    }
}
