package com.promptstudio.relay.exception;

public class RelayParticipantLeftException extends RuntimeException {

    public RelayParticipantLeftException(Long roomId) {
        super("릴레이 방 ID " + roomId + "에서 이미 이탈하여 다시 입장할 수 없습니다.");
    }
}
