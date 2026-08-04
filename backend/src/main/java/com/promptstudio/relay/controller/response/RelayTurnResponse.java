package com.promptstudio.relay.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "릴레이 턴 전송 결과. 대기 중인 참가자들은 같은 내용을 WebSocket의 "
        + "turn.finished + room.state 이벤트로 받는다")
public record RelayTurnResponse(
        @Schema(description = "완료된 턴의 요약")
        RelayTurnSummaryResponse turn,
        @Schema(description = "턴 반영 후의 방 상태. 다음 좌석 또는 FINISHED")
        RelayRoomResponse room
) {

    @Schema(description = "완료된 릴레이 턴 하나의 요약. 프롬프트 원문은 싣지 않는다")
    public record RelayTurnSummaryResponse(
            @Schema(description = "릴레이 진행 인덱스(0-based)", example = "3")
            int turnIndex,
            @Schema(description = "이 턴을 친 좌석(0-based)", example = "1")
            int seatOrder,
            @Schema(description = "이 턴이 몇 바퀴째였는지(0-based)", example = "1")
            int lap,
            @Schema(description = "이 턴을 친 사용자 ID", example = "7")
            Long authorUserId,
            @Schema(description = "AI가 수행한 작업의 한국어 요약")
            String aiSummary,
            @Schema(description = "이 턴에서 바뀐 파일 경로들. 내용은 코드 조회 API로 받는다")
            List<String> changedPaths
    ) {
    }
}
