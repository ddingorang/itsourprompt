package com.promptstudio.relay.repository;

import com.promptstudio.relay.domain.RelayRoom;
import com.promptstudio.relay.domain.RelayRoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RelayRoomJpaRepository extends JpaRepository<RelayRoom, Long>, RelayRoomRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RelayRoom r where r.id = :id")
    Optional<RelayRoom> findByIdForUpdate(@Param("id") Long id);

    @Override
    Optional<RelayRoom> findByCurrentRunId(UUID runId);

    @Override
    default List<RelayRoom> findWaitingRooms() {
        return findTop50ByStatusOrderByCreatedAtDesc(RelayRoomStatus.WAITING);
    }

    List<RelayRoom> findTop50ByStatusOrderByCreatedAtDesc(RelayRoomStatus status);

    @Override
    @Query("""
            select r from RelayRoom r
            where r.status = com.promptstudio.relay.domain.RelayRoomStatus.TURN_GRADING
              and r.turnDeadline < :now
            """)
    List<RelayRoom> findExpiredGrading(@Param("now") Instant now);

    @Override
    @Query("""
            select r from RelayRoom r
            where r.status = com.promptstudio.relay.domain.RelayRoomStatus.PLAYING
              and r.turnDeadline < :now
            """)
    List<RelayRoom> findExpiredTurnInput(@Param("now") Instant now);

    @Override
    default boolean tryClaimTurn(Long roomId, int expectedTurnIndex) {
        return transition(roomId, expectedTurnIndex, RelayRoomStatus.PLAYING, RelayRoomStatus.TURN_GENERATING) == 1;
    }

    /**
     * clearAutomatically가 필요하다. 벌크 UPDATE는 영속성 컨텍스트를 우회하므로, 지우지 않으면
     * 같은 트랜잭션에서 다시 읽은 방이 전이 전 상태로 보인다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update RelayRoom r set r.status = :next
            where r.id = :id and r.currentTurnIndex = :turnIndex and r.status = :expected
            """)
    int transition(
            @Param("id") Long roomId,
            @Param("turnIndex") int turnIndex,
            @Param("expected") RelayRoomStatus expected,
            @Param("next") RelayRoomStatus next
    );
}
