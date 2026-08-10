package com.promptstudio.relay.domain;

import java.time.Instant;

/**
 * 로비 목록의 방 한 줄. 들어갈지 결정하는 데 필요한 것만 담는다 —
 * 참가자 명단 같은 상세는 방에 들어가서(스냅샷으로) 본다.
 */
public record RelayRoomSummary(
        Long roomId,
        String name,
        Long problemId,
        String problemTitle,
        Long hostUserId,
        String hostNickname,
        int participantCount,
        int maxParticipants,
        int totalLaps,
        int turnTimeLimitSeconds,
        Instant createdAt
) {
}
