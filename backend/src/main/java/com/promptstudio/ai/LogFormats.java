package com.promptstudio.ai;

/**
 * AI 호출 실패를 남길 때 쓰는 로그 포맷. 응답 본문은 길이가 제한 없이 커질 수 있어 그대로 싣지 않는다.
 */
final class LogFormats {

    private static final int MAX_LOGGED_RESPONSE_BODY_LENGTH = 4_000;

    private LogFormats() {
    }

    static String abbreviate(String value) {
        if (value == null || value.length() <= MAX_LOGGED_RESPONSE_BODY_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_LOGGED_RESPONSE_BODY_LENGTH) + "... (truncated)";
    }
}
