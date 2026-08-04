package com.promptstudio.relay.repository;

import com.promptstudio.relay.domain.RelayParticipant;

import java.util.List;
import java.util.Optional;

public interface RelayParticipantRepository {

    RelayParticipant save(RelayParticipant participant);

    void delete(RelayParticipant participant);

    /**
     * 입장 순서대로. 좌석 번호는 이 순서로 부여하므로 정렬 기준이 곧 게임 규칙이다.
     */
    List<RelayParticipant> findByRoomIdOrderByJoinedAt(Long roomId);

    Optional<RelayParticipant> findByRoomIdAndUserId(Long roomId, Long userId);

    /**
     * 아직 나가지 않은 참가자 수. 정원 검사에 쓴다.
     */
    long countActiveByRoomId(Long roomId);

    /**
     * 여러 방의 활성 참가자를 한 번에 읽는다. 로비 목록이 방마다 인원을 세는 N+1을 피한다.
     */
    List<RelayParticipant> findByRoomIdInAndLeftAtIsNull(List<Long> roomIds);
}
