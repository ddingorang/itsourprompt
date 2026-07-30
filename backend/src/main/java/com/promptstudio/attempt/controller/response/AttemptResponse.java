package com.promptstudio.attempt.controller.response;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.domain.ToolCallEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "어템프트 상태")
public record AttemptResponse(
        @Schema(description = "어템프트 ID", example = "1")
        Long id,
        @Schema(description = "풀이 중인 문제 ID", example = "1")
        Long problemId,
        @Schema(description = "어템프트를 시작한 문제 스켈레톤 파일 전체")
        List<AttemptFileResponse> baseFiles,
        @Schema(description = "현재 프로젝트 파일 전체")
        List<AttemptFileResponse> files,
        @Schema(description = "지금까지 진행한 턴 목록")
        List<TurnResponse> turns,
        @Schema(description = "어템프트 상태", example = "IN_PROGRESS")
        AttemptStatus status
) {

    @Schema(description = "어템프트의 현재 파일")
    public record AttemptFileResponse(
            @Schema(description = "파일 경로", example = "src/main/java/Main.java")
            String path,
            @Schema(description = "파일 내용")
            String content
    ) {
    }

    @Schema(description = "어템프트의 턴 기록")
    public record TurnResponse(
            @Schema(description = "사용자가 보낸 작업 요청")
            String prompt,
            @Schema(description = "AI의 작업 요약")
            String aiResponse,
            @Schema(description = "직전 상태 대비 변경 파일 목록")
            List<ChangedFileResponse> changedFiles,
            @Schema(description = "AI가 호출한 툴 기록")
            List<ToolCallResponse> toolCalls
    ) {
    }

    @Schema(description = "턴에서 AI가 호출한 툴")
    public record ToolCallResponse(
            @Schema(description = "툴 이름", example = ToolCallEntry.EDIT_FILE)
            String tool,
            @Schema(description = "대상 파일 경로. 대상이 없는 툴은 null", example = "src/main/java/Main.java")
            String path
    ) {
    }

    @Schema(description = "턴에서 변경된 파일")
    public record ChangedFileResponse(
            @Schema(description = "변경된 파일 경로", example = "src/main/java/Main.java")
            String path,
            @Schema(description = "파일 변경 유형", example = "MODIFIED")
            FileChange.ChangeType changeType,
            @Schema(description = "변경 후 파일 전체 내용. 삭제된 파일은 없다.")
            String content
    ) {
    }
}
