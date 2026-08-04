package com.promptstudio.relay.repository;

import com.promptstudio.relay.domain.RelayTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RelayTurnJpaRepository extends JpaRepository<RelayTurn, Long>, RelayTurnRepository {

    @Override
    List<RelayTurn> findByRoomIdOrderByTurnIndex(Long roomId);

    @Override
    java.util.Optional<RelayTurn> findByRoomIdAndTurnIndex(Long roomId, int turnIndex);
}
