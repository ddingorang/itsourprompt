package com.promptstudio.relay.exception;

public class RelayGameNotStartedException extends RuntimeException {

    public RelayGameNotStartedException(Long roomId) {
        super("릴레이 방 ID " + roomId + "는 아직 게임이 시작되지 않았습니다.");
    }
}
