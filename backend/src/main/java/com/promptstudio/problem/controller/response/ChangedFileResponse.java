package com.promptstudio.problem.controller.response;

import com.promptstudio.problem.domain.FileChange;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "스켈레톤 대비 변경된 파일")
public record ChangedFileResponse(
        @Schema(description = "변경된 파일 경로", example = "src/main/java/Main.java")
        String path,
        @Schema(description = "파일 변경 유형", example = "MODIFIED")
        FileChange.ChangeType changeType
) {
}
