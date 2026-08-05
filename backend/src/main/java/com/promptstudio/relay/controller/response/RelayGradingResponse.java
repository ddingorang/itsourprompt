package com.promptstudio.relay.controller.response;

import com.promptstudio.attempt.domain.CodeRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "턴 하나의 채점 결과. WebSocket grading.finished 이벤트의 payload와 같은 모양이다")
public record RelayGradingResponse(
        @Schema(description = "채점된 턴의 릴레이 진행 인덱스(0-based)", example = "3")
        int turnIndex,
        @Schema(description = "이 턴을 친 사용자 ID", example = "7")
        Long authorUserId,
        @Schema(description = "실행 판정. 채점을 얻지 못하고 건너뛰었으면 null", example = "TEST_FAILED")
        CodeRunStatus runStatus,
        @Schema(description = "통과한 테스트 수. null이면 0개 통과가 아니라 채점 없음이다", example = "3")
        Integer passed,
        @Schema(description = "전체 테스트 수", example = "5")
        Integer total,
        @Schema(
                description = "직전 통과 수 대비 증가분 = 이 주자의 기여도. 앞사람의 통과를 깨뜨리면 "
                        + "음수가 나온다. 기준이 없으면 null",
                example = "2"
        )
        Integer delta,
        @Schema(description = "실패한 케이스들. 이름이 한글 요구사항 문장이라 그대로 화면에 보여줄 수 있다")
        List<FailedCaseResponse> failedCases,
        @Schema(description = "채점 결과를 얻지 못하고 전진했는지", example = "false")
        boolean skipped
) {

    @Schema(description = "실패한 테스트 케이스 하나")
    public record FailedCaseResponse(
            @Schema(description = "테스트 이름", example = "전화번호_가운데_자리를_마스킹한다")
            String name,
            @Schema(description = "실패 사유 한 줄. 없으면 null")
            String message
    ) {
    }
}
