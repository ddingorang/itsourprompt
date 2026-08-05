package com.promptstudio.relay.exception;

public class NotRelayHostException extends RuntimeException {

    public NotRelayHostException(Long roomId) {
        super("릴레이 방 ID " + roomId + "의 게임 시작 권한은 방장에게만 있습니다.");
    }
}
