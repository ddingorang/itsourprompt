package com.promptstudio.relay.controller.response;

import com.promptstudio.relay.domain.RelayRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "릴레이 방 상태. 재접속한 참가자는 이것만 받아 화면을 복원할 수 있다")
public record RelayRoomResponse(
        @Schema(description = "방 ID", example = "1")
        Long roomId,
        @Schema(description = "풀고 있는 문제 ID", example = "1")
        Long problemId,
        @Schema(description = "방장 사용자 ID. 게임 시작 권한이 있다", example = "7")
        Long hostUserId,
        @Schema(description = "진행 단계", example = "WAITING")
        RelayRoomStatus status,
        @Schema(description = "몇 바퀴를 도는지", example = "2")
        int totalLaps,
        @Schema(description = "정원", example = "4")
        int maxParticipants,
        @Schema(description = "시작 시점에 확정된 좌석 수. 시작 전에는 null", example = "4")
        Integer seatCount,
        @Schema(
                description = "릴레이 진행 인덱스(0-based). 좌석은 seatCount로 나눈 나머지, 바퀴는 몫이다",
                example = "0"
        )
        int currentTurnIndex,
        @Schema(description = "지금 차례인 좌석(0-based). 릴레이 진행 중이 아니면 null", example = "1")
        Integer currentSeat,
        @Schema(description = "지금 몇 바퀴째인지(0-based). currentSeat와 같은 조건에서만 값이 있다", example = "0")
        Integer currentLap,
        @Schema(description = "총 턴 수 = 좌석 × 바퀴. 좌석 확정 전에는 null", example = "8")
        Integer totalTurns,
        @Schema(
                description = "시작 스켈레톤이 통과시킨 테스트 수 — 첫 주자 점수의 기준선. "
                        + "베이스라인 채점 결과가 오기 전에는 null",
                example = "0"
        )
        Integer baselinePassed,
        @Schema(description = "베이스라인 채점의 전체 테스트 수", example = "5")
        Integer baselineTotal,
        @Schema(description = "현재 턴의 제한시간. 진행 중이 아니면 null", example = "2026-08-04T02:33:28Z")
        Instant turnDeadline,
        @Schema(description = "참가자 목록. 입장 순서대로 온다")
        List<RelayParticipantResponse> participants,
        @Schema(description = "방을 만든 시각", example = "2026-08-04T02:30:00Z")
        Instant createdAt,
        @Schema(description = "게임을 시작한 시각. 시작 전에는 null", example = "2026-08-04T02:32:10Z")
        Instant startedAt,
        @Schema(description = "게임이 끝난 시각. 종료 전에는 null", example = "2026-08-04T02:45:00Z")
        Instant finishedAt
) {

    @Schema(description = "방 참가자")
    public record RelayParticipantResponse(
            @Schema(description = "사용자 ID", example = "7")
            Long userId,
            @Schema(description = "표시 이름", example = "김상현")
            String nickname,
            @Schema(
                    description = "풀이 순서(0-based). 게임 시작 시점에 입장 순서로 부여되며 시작 전에는 null",
                    example = "0"
            )
            Integer seatOrder,
            @Schema(description = "입장 시각", example = "2026-08-04T02:30:05Z")
            Instant joinedAt,
            @Schema(
                    description = "게임 중 이탈했는지. 이탈해도 좌석은 남고 그 좌석의 턴은 제한시간 뒤 스킵된다",
                    example = "false"
            )
            boolean left
    ) {
    }
}
