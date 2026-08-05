package com.promptstudio.relay.exception;

public class RelayNotYourTurnException extends RuntimeException {

    public RelayNotYourTurnException(Long roomId, Integer currentSeat) {
        super("릴레이 방 ID " + roomId + "에서 지금은 " + currentSeat + "번 좌석의 차례입니다.");
    }
}
