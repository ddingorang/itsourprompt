package com.promptstudio.relay.exception;

public class RelayRoomNotFoundException extends RuntimeException {

    public RelayRoomNotFoundException(Long roomId) {
        super("릴레이 방 ID " + roomId + "를 찾을 수 없습니다.");
    }
}
