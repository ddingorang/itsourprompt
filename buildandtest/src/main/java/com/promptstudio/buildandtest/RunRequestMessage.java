package com.promptstudio.buildandtest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * run.request 와이어 포맷. 백엔드의 {@code com.promptstudio.rabbit.RunRequestMessage}와 같은 형태다.
 *
 * <p>계약 진화 규칙: 필드는 추가만 하고 이름 변경·삭제는 하지 않는다. 새 필드는 항상 옵셔널로 둔다.
 * 한쪽만 배포된 시점에도 메시지가 처리되도록 관용적 리더로 동작한다.
 *
 * @param language  문제의 채점 언어(java·python). 필드 도입 전의 메시지는 null이며 java로 해석한다.
 *                  언어별 실행기 선택은 아직 없다 — 이 워커는 종전대로 java로 처리한다.
 * @param files     어템프트의 현재 파일. 사용자와 AI가 고칠 수 있으므로 신뢰할 수 없다.
 * @param testFiles 문제의 채점용 테스트. 비어 있으면 종전대로 main만 실행한다.
 *                  경로가 {@code files}와 겹치면 이쪽이 이긴다({@link Workspace} 참고).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunRequestMessage(
        UUID runId,
        Long attemptId,
        String language,
        List<RunFileMessage> files,
        List<RunFileMessage> testFiles
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunFileMessage(String path, String content) {
    }
}
