package com.promptstudio.attempt.exception;

public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String idempotencyKey) {
        super("이미 처리 중인 요청입니다. (Idempotency-Key: " + idempotencyKey + ")");
    }
}
