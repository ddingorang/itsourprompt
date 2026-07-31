package com.promptstudio.attempt.exception;

public class TurnNotFoundException extends RuntimeException {

    public TurnNotFoundException(Long attemptId, int turnOrdinal, int turnCount) {
        super("어템프트 ID " + attemptId + "에 " + turnOrdinal + "번째 턴이 없습니다. 턴은 " + turnCount + "개입니다.");
    }
}
