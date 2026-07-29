package com.promptstudio.problem.service;

public class ProblemSyncFormatException extends RuntimeException {

    public ProblemSyncFormatException(String slug, String reason) {
        super("문제 디렉토리 " + slug + "의 형식이 올바르지 않습니다: " + reason);
    }
}
