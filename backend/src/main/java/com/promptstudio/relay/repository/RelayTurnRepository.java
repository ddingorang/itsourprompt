package com.promptstudio.relay.repository;

import com.promptstudio.relay.domain.RelayTurn;

import java.util.List;

public interface RelayTurnRepository {

    RelayTurn save(RelayTurn turn);

    List<RelayTurn> findByRoomIdOrderByTurnIndex(Long roomId);

    java.util.Optional<RelayTurn> findByRoomIdAndTurnIndex(Long roomId, int turnIndex);
}
