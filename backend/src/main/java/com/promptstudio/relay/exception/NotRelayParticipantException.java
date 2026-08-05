package com.promptstudio.relay.exception;

public class NotRelayParticipantException extends RuntimeException {

    public NotRelayParticipantException(Long roomId) {
        super("릴레이 방 ID " + roomId + "의 참가자가 아닙니다.");
    }
}
