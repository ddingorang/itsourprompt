package com.promptstudio.problem.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "문제 목록 응답")
public record ProblemListResponse(
        @Schema(description = "문제 요약 목록")
        List<ProblemSummaryResponse> problems
) {

    @Schema(description = "목록에 표시할 문제 요약 정보")
    public record ProblemSummaryResponse(
            @Schema(description = "문제 ID", example = "1")
            Long id,
            @Schema(description = "문제 제목", example = "Hello World 출력")
            String title,
            @Schema(description = "문제 타입", example = "coding", allowableValues = {"coding", "game"})
            String type,
            @Schema(description = "풀이 언어", example = "java")
            String language
    ) {
    }
}
