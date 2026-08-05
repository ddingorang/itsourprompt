package com.promptstudio.relay.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "입장 가능한 릴레이 방 목록. 최신 개설 순")
public record RelayRoomListResponse(
        @Schema(description = "입장을 받는 중인 방들. 없으면 빈 배열")
        List<RelayRoomSummaryResponse> rooms
) {

    @Schema(description = "로비 목록의 방 한 줄")
    public record RelayRoomSummaryResponse(
            @Schema(description = "방 ID. 입장은 POST /relay/rooms/{id}/participants", example = "3")
            Long roomId,
            @Schema(description = "방장이 붙인 방 이름. 이름 도입 전에 만들어진 방은 null", example = "점심시간 한 판")
            String name,
            @Schema(description = "풀 문제 ID", example = "2")
            Long problemId,
            @Schema(description = "풀 문제 제목", example = "전화번호 개인정보 보호 처리")
            String problemTitle,
            @Schema(description = "방장 사용자 ID", example = "7")
            Long hostUserId,
            @Schema(description = "방장 표시 이름", example = "프롬프터")
            String hostNickname,
            @Schema(description = "현재 인원", example = "2")
            int participantCount,
            @Schema(description = "정원", example = "4")
            int maxParticipants,
            @Schema(description = "몇 바퀴를 도는지", example = "1")
            int totalLaps,
            @Schema(description = "방을 만든 시각", example = "2026-08-04T09:30:00Z")
            Instant createdAt
    ) {
    }
}
