package com.promptstudio.ranking.exception;

public class RankingLimitOutOfRangeException extends RuntimeException {

    public RankingLimitOutOfRangeException(int limit, int min, int max) {
        super("limit은 " + min + " 이상 " + max + " 이하여야 합니다. 요청 값: " + limit);
    }
}
