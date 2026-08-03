package com.promptstudio.attempt.controller.response;

import com.promptstudio.attempt.domain.CodeRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 맨 배열이 아니라 봉투로 감싼다. 응답 스키마는 필드 추가 방식으로만 확장한다는 규약(docs/api.md)을
 * 지키려면 나중에 총 개수나 페이지 정보를 얹을 자리가 있어야 하고, 배열을 그대로 내보내면 그 시점에
 * 클라이언트를 깨뜨려야 한다.
 */
@Schema(description = "코드 빌드/실행 목록 응답")
public record CodeRunListResponse(
        @Schema(description = "실행 목록. 최근 실행이 먼저 온다. 실행이 없으면 빈 배열")
        List<CodeRunSummaryResponse> runs
) {

    @Schema(description = "목록에 표시할 실행 요약. stdout·stderr는 담지 않으므로 본문은 단건 조회로 가져온다")
    public record CodeRunSummaryResponse(
            @Schema(description = "실행 ID", example = "3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071")
            UUID runId,
            @Schema(
                    description = "실행한 코드가 몇 번째 턴의 것인지(0-based). 시작 스켈레톤을 실행했거나 "
                            + "턴 단위 기록 이전의 실행이면 null",
                    example = "2"
            )
            Integer turnOrdinal,
            @Schema(description = "실행 상태", example = "SUCCEEDED")
            CodeRunStatus status,
            @Schema(description = "종료 코드. 종료 전이거나 타임아웃이면 null", example = "0")
            Integer exitCode,
            @Schema(description = "실행에 걸린 시간(ms). 종료 전에는 null", example = "1840")
            Long durationMs,
            @Schema(description = "실행을 접수한 시각", example = "2026-08-03T02:33:28Z")
            Instant createdAt,
            @Schema(description = "실행이 끝난 시각. 아직 QUEUED면 null", example = "2026-08-03T02:33:29Z")
            Instant finishedAt
    ) {
    }
}
