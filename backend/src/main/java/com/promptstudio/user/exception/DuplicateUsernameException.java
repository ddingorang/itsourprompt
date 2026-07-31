package com.promptstudio.user.exception;

public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("이미 사용 중인 아이디입니다: " + username);
    }
}
