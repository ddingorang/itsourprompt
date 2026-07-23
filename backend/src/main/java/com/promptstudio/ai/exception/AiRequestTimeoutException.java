package com.promptstudio.ai.exception;

public class AiRequestTimeoutException extends RuntimeException {

    public AiRequestTimeoutException() {
        super("AI 코드 생성 요청 시간이 초과되었습니다.");
    }
}
