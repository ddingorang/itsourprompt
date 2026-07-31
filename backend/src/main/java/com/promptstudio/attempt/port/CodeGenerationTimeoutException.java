package com.promptstudio.attempt.port;

public class CodeGenerationTimeoutException extends RuntimeException {

    public CodeGenerationTimeoutException() {
        super("AI 코드 생성 요청 시간이 초과되었습니다.");
    }
}
