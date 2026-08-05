package com.promptstudio.attempt.domain;

/**
 * FAILED는 실패한 호출 자체의 토큰이 아니라 실패가 있었다는 마커다 — 응답을 받지 못해 사용량을 알 수 없다.
 */
public enum LlmCallStatus {
    SUCCESS, FAILED
}
