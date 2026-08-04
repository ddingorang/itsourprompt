package com.promptstudio.relay.exception;

public class RelayRoomAlreadyStartedException extends RuntimeException {

    public RelayRoomAlreadyStartedException(Long roomId) {
        super("릴레이 방 ID " + roomId + "는 이미 게임이 시작되었습니다.");
    }
}
