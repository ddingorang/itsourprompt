package com.promptstudio.relay.domain;

import java.time.Instant;
import java.util.List;

/**
 * 방의 현재 상태 스냅샷. 재접속한 참가자가 이것만 받아 화면을 복원할 수 있어야 한다 —
 * 게임 상태는 서버가 진실의 원천이고 클라이언트가 누적해 들고 있는 값은 없다.
 *
 * @param seatCount      시작 전에는 null. 시작 시점의 참가자 수로 확정된다
 * @param currentSeat    지금 차례인 좌석. 릴레이 진행 중이 아니면 null
 * @param currentLap     지금 몇 바퀴째인지(0-based). currentSeat와 같은 조건에서만 값이 있다
 * @param totalTurns     좌석 × 바퀴. 좌석 확정 전에는 null
 * @param baselinePassed 시작 스켈레톤이 통과시킨 테스트 수 — 첫 주자 점수의 기준선.
 *                       베이스라인 채점 결과가 오기 전에는 null
 * @param name           방장이 붙인 방 이름. 이름 도입 전에 만들어진 방은 null
 */
public record RelayRoomView(
        Long id,
        String name,
        Long problemId,
        Long hostUserId,
        RelayRoomStatus status,
        int totalLaps,
        int maxParticipants,
        int turnTimeLimitSeconds,
        Integer seatCount,
        int currentTurnIndex,
        Integer currentSeat,
        Integer currentLap,
        Integer totalTurns,
        Integer baselinePassed,
        Integer baselineTotal,
        Instant turnDeadline,
        List<RelayParticipantView> participants,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {

    public static RelayRoomView of(RelayRoom room, List<RelayParticipantView> participants) {
        return new RelayRoomView(
                room.id(),
                room.name(),
                room.problemId(),
                room.hostUserId(),
                room.status(),
                room.totalLaps(),
                room.maxParticipants(),
                room.turnTimeLimitSeconds(),
                room.seatCount(),
                room.currentTurnIndex(),
                room.currentSeat(),
                room.currentLap(),
                room.totalTurns(),
                room.baselinePassed(),
                room.baselineTotal(),
                room.turnDeadline(),
                List.copyOf(participants),
                room.createdAt(),
                room.startedAt(),
                room.finishedAt()
        );
    }

    /**
     * @param seatOrder 시작 전에는 null. 화면은 그때까지 목록 순서(=입장 순서)를 그대로 보여주면 된다
     * @param nickname  users에서 조회한 표시 이름
     */
    public record RelayParticipantView(
            Long userId,
            String nickname,
            Integer seatOrder,
            Instant joinedAt,
            boolean left
    ) {
    }
}
