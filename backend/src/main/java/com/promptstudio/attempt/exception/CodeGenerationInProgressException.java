package com.promptstudio.attempt.exception;

public class CodeGenerationInProgressException extends RuntimeException {

    public CodeGenerationInProgressException(Long attemptId) {
        super("다른 창에서 이 문제의 AI 요청을 처리하고 있습니다. 잠시 후 새로고침 후 다시 시도해 주시기 바랍니다.");
    }
}
