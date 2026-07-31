package com.promptstudio.attempt.controller.response;

import com.promptstudio.attempt.domain.CodeRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "코드 빌드/실행 상태")
public record CodeRunResponse(
        @Schema(description = "실행 ID", example = "3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071")
        UUID runId,
        @Schema(
                description = "실행한 코드가 몇 번째 턴의 것인지(0-based). 턴을 적용하지 않은 시작 스켈레톤을 "
                        + "실행했거나 턴 단위 기록 이전의 실행이면 null",
                example = "2"
        )
        Integer turnOrdinal,
        @Schema(description = "실행 상태", example = "QUEUED")
        CodeRunStatus status,
        @Schema(description = "java 프로세스 종료 코드. 종료 전에는 null", example = "0")
        Integer exitCode,
        @Schema(description = "표준 출력. 종료 전에는 null")
        String stdout,
        @Schema(description = "표준 에러. 컴파일 오류 메시지가 여기 담긴다. 종료 전에는 null")
        String stderr,
        @Schema(description = "실행에 걸린 시간(ms). 종료 전에는 null", example = "1840")
        Long durationMs
) {
}
