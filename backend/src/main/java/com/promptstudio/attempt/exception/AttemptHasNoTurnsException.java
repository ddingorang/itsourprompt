package com.promptstudio.attempt.exception;

public class AttemptHasNoTurnsException extends RuntimeException {

    public AttemptHasNoTurnsException(Long attemptId) {
        super("어템프트 ID " + attemptId + "에 턴이 없어 피드백을 생성할 수 없습니다.");
    }
}
