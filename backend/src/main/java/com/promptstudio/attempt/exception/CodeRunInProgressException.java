package com.promptstudio.attempt.exception;

public class CodeRunInProgressException extends RuntimeException {

    public CodeRunInProgressException(Long attemptId) {
        super("어템프트 ID " + attemptId + "는 코드 실행이 진행 중입니다.");
    }
}
