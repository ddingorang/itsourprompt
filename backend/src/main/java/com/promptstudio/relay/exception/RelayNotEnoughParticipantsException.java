package com.promptstudio.relay.exception;

public class RelayNotEnoughParticipantsException extends RuntimeException {

    public RelayNotEnoughParticipantsException(Long roomId, int minParticipants) {
        super("릴레이 방 ID " + roomId + "는 최소 " + minParticipants + "명이 있어야 시작할 수 있습니다.");
    }
}
