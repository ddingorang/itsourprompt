package com.promptstudio.problem.exception;

public class ProblemNotFoundException extends RuntimeException {

    public ProblemNotFoundException(Long problemId) {
        super("문제 ID " + problemId + "를 찾을 수 없습니다.");
    }
}
