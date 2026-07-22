package com.promptstudio.problem.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "문제 상세 응답")
public record ProblemDetailResponse(
        @Schema(description = "문제 ID", example = "1")
        Long id,
        @Schema(description = "문제 제목", example = "Hello World 출력")
        String title,
        @Schema(description = "Markdown 형식의 문제 명세")
        String specMd,
        @Schema(description = "문제에서 제공하는 스켈레톤 파일 목록")
        List<ProblemFileResponse> files
) {

    @Schema(description = "스켈레톤 파일")
    public record ProblemFileResponse(
            @Schema(description = "파일 경로", example = "src/main/java/Main.java")
            String path,
            @Schema(description = "파일 내용")
            String content
    ) {
    }
}
