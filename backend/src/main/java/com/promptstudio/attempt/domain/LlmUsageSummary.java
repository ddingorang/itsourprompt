package com.promptstudio.attempt.domain;

import java.math.BigDecimal;

/**
 * 턴 하나가 쓴 사용량 합계. 원본 호출 행에서 파생하며 저장하지 않는다.
 *
 * @param uncachedInputTokens 캐시가 안 먹은 입력 토큰. 단가가 10배 가까이 차이 나므로 비용을 읽으려면 이
 *                            구분이 필요하다. 호출마다 입력에서 캐시 적중분을 뺀 값을 더한 것이라, 입력
 *                            토큰을 모르는 호출이 섞이면 cachedInputTokens와 더해도 inputTokens가 아니다
 * @param latencyMs           LLM 호출들의 왕복 시간 합. 툴 실행·파싱 시간은 들어가지 않는다
 * @param cost                단가를 모르는 모델이 섞이면 그만큼 빠진 합계다
 * @param rounds              그 턴의 LLM 호출 횟수
 */
public record LlmUsageSummary(
        Long inputTokens,
        Long uncachedInputTokens,
        Long cachedInputTokens,
        Long outputTokens,
        Long reasoningTokens,
        Long latencyMs,
        BigDecimal cost,
        String model,
        int rounds
) {
}
