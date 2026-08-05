package com.promptstudio.relay.exception;

public class RelayRoomFullException extends RuntimeException {

    public RelayRoomFullException(Long roomId, int maxParticipants) {
        super("릴레이 방 ID " + roomId + "의 정원(" + maxParticipants + "명)이 가득 찼습니다.");
    }
}
