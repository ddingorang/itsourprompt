package com.promptstudio.attempt.domain;

import java.math.BigDecimal;

/**
 * 어템프트의 턴 합계. 사용자가 자기 프롬프트의 효율을 보는 지표라, 턴에 속하지 않는 호출(제출 시
 * 피드백 생성, 실패로 턴이 저장되지 않은 flush)은 뺀다 — 그건 서비스가 부담하는 비용이다.
 *
 * <p>그래서 턴별 합계를 다 더하면 정확히 이 값이 된다. 턴은 지워지지 않는다는 전제 위에 서 있다 —
 * 턴을 지우는 경로가 생기면 호출 행이 고아로 남아 이 등식이 깨진다. 실제 과금 총액은 원본 행에서
 * 따로 집계한다.
 *
 * @param uncachedInputTokens 캐시가 안 먹은 입력 토큰. {@link LlmUsageSummary}와 같은 규칙으로 더한다
 * @param rounds              어템프트의 턴들이 낸 LLM 호출 횟수 합
 */
public record LlmUsageTotals(
        Long inputTokens,
        Long uncachedInputTokens,
        Long cachedInputTokens,
        Long outputTokens,
        Long reasoningTokens,
        Long latencyMs,
        BigDecimal cost,
        int rounds
) {
}
