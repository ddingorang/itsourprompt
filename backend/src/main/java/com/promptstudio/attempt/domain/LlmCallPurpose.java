package com.promptstudio.attempt.domain;

/**
 * 호출을 낸 워크로드. 같은 어템프트에서도 코드 생성과 피드백은 모델·단가가 다르다.
 *
 * <p>제출 한 번은 FEEDBACK과 PATTERN_FEEDBACK 두 호출을 낸다. 실패 경로는 둘을 가르지 않고
 * FEEDBACK 하나로 남긴다 — 어느 쪽이 먼저 무너졌는지는 이미 로그에 있다.
 */
public enum LlmCallPurpose {
    CODE, FEEDBACK, PATTERN_FEEDBACK
}
