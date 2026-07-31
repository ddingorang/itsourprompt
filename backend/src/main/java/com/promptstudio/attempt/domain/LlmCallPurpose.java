package com.promptstudio.attempt.domain;

/**
 * 호출을 낸 워크로드. 같은 어템프트에서도 코드 생성과 피드백은 모델·단가가 다르다.
 */
public enum LlmCallPurpose {
    CODE, FEEDBACK
}
