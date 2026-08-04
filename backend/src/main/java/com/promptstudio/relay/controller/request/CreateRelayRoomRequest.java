package com.promptstudio.relay.controller.request;

import com.promptstudio.relay.domain.RelayRoom;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "릴레이 방 개설 요청")
public record CreateRelayRoomRequest(
        @NotNull(message = "problemId는 비어 있을 수 없습니다.")
        @Schema(description = "릴레이로 풀 문제 ID", example = "1")
        Long problemId,

        @NotNull(message = "totalLaps는 비어 있을 수 없습니다.")
        @Min(value = RelayRoom.MIN_LAPS, message = "totalLaps는 " + RelayRoom.MIN_LAPS + " 이상이어야 합니다.")
        @Max(value = RelayRoom.MAX_LAPS, message = "totalLaps는 " + RelayRoom.MAX_LAPS + " 이하여야 합니다.")
        @Schema(
                description = "몇 바퀴를 돌지. 총 턴 수는 참가자 수 × 바퀴 수이고, 턴 하나가 코드 생성과 "
                        + "채점으로 1분 가까이 걸린다",
                example = "2"
        )
        Integer totalLaps,

        @NotNull(message = "maxParticipants는 비어 있을 수 없습니다.")
        @Min(
                value = RelayRoom.MIN_PARTICIPANTS,
                message = "maxParticipants는 " + RelayRoom.MIN_PARTICIPANTS + " 이상이어야 합니다."
        )
        @Max(
                value = RelayRoom.MAX_PARTICIPANTS,
                message = "maxParticipants는 " + RelayRoom.MAX_PARTICIPANTS + " 이하여야 합니다."
        )
        @Schema(
                description = "정원. WebRTC를 mesh로 붙이므로 상한이 있다",
                example = "4"
        )
        Integer maxParticipants
) {
}
