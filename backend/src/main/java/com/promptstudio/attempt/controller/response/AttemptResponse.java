package com.promptstudio.attempt.controller.response;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.FileChange;
import com.promptstudio.attempt.domain.ToolCallEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
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
        AttemptStatus status,
        @Schema(description = "어템프트 전체의 LLM 사용량 총계. 기록이 없으면 null")
        AttemptUsageResponse usage
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
            List<ToolCallResponse> toolCalls,
            @Schema(description = "이 턴의 LLM 사용량 합계. 사용량 기록 도입 전 턴은 null")
            TurnUsageResponse usage
    ) {
    }

    @Schema(description = "턴 하나가 쓴 LLM 사용량 합계")
    public record TurnUsageResponse(
            @Schema(description = "입력 토큰 합계", example = "2500")
            Long inputTokens,
            @Schema(description = "출력 토큰 합계", example = "500")
            Long outputTokens,
            @Schema(description = "캐시 적중 입력 토큰 합계", example = "1000")
            Long cachedInputTokens,
            @Schema(description = "추론 토큰 합계", example = "120")
            Long reasoningTokens,
            @Schema(description = "USD 비용. 단가가 등록되지 않은 모델은 null", example = "0.00300000")
            BigDecimal cost,
            @Schema(description = "호출에 쓴 모델", example = "gpt-5.6-luna")
            String model,
            @Schema(description = "이 턴의 LLM 호출 횟수", example = "2")
            int rounds
    ) {
    }

    @Schema(description = "어템프트 전체의 LLM 사용량 총계. 턴에 속하지 않는 피드백·실패 호출까지 포함한다")
    public record AttemptUsageResponse(
            @Schema(description = "입력 토큰 합계", example = "4500")
            Long inputTokens,
            @Schema(description = "출력 토큰 합계", example = "900")
            Long outputTokens,
            @Schema(description = "캐시 적중 입력 토큰 합계", example = "1000")
            Long cachedInputTokens,
            @Schema(description = "추론 토큰 합계", example = "220")
            Long reasoningTokens,
            @Schema(description = "USD 비용 합계. 단가가 등록되지 않은 모델은 빠진다", example = "0.00580000")
            BigDecimal cost
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
