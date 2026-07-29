package com.promptstudio.attempt.exception;

public class InactiveProblemException extends RuntimeException {

    public InactiveProblemException(Long problemId) {
        super("문제 ID " + problemId + "는 비활성 상태여서 새로 시작할 수 없습니다.");
    }
}
