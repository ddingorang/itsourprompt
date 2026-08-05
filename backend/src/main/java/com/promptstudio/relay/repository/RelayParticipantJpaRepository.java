package com.promptstudio.relay.repository;

import com.promptstudio.relay.domain.RelayParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface RelayParticipantJpaRepository extends JpaRepository<RelayParticipant, Long>, RelayParticipantRepository {

    @Override
    List<RelayParticipant> findByRoomIdOrderByJoinedAt(Long roomId);

    @Override
    Optional<RelayParticipant> findByRoomIdAndUserId(Long roomId, Long userId);

    @Override
    @Query("select count(p) from RelayParticipant p where p.roomId = :roomId and p.leftAt is null")
    long countActiveByRoomId(@Param("roomId") Long roomId);

    @Override
    List<RelayParticipant> findByRoomIdInAndLeftAtIsNull(List<Long> roomIds);
}
