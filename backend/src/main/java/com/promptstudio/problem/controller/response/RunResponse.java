package com.promptstudio.problem.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "AI 코드 실행 결과")
public record RunResponse(
        @Schema(description = "AI 작업 후 최종 파일 전체")
        List<RunFileResponse> files,
        @Schema(description = "스켈레톤 대비 변경 파일 목록")
        List<ChangedFileResponse> changedFiles,
        @Schema(description = "AI의 작업 요약")
        String aiResponse
) {

    @Schema(description = "AI 작업 후 파일")
    public record RunFileResponse(
            @Schema(description = "파일 경로", example = "src/main/java/Main.java")
            String path,
            @Schema(description = "파일 내용")
            String content
    ) {
    }
}
