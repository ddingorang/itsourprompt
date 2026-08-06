package com.promptstudio.ranking.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 랭킹 한 줄. 어템프트 1건이 1줄이므로 한 사람이 여러 줄을 차지할 수 있다.
 *
 * @param rank     동점은 같은 등수를 받고 다음 등수는 건너뛴다
 * @param userId   이 줄의 주인. 랭킹에는 로그인 사용자의 제출만 들어오므로 항상 채워진다.
 *                 요청자가 자기 줄을 <b>전부</b> 알아보려면 신원이 필요하다 — 등수나 어템프트 ID로
 *                 견주면 최고 기록 한 줄만 자기 것으로 표시된다.
 *                 응답에는 나가지 않는다(남의 사용자 ID를 공개 조회로 흘리면 안 된다)
 * @param nickname 주인의 닉네임
 * @param cost     저장된 지출 기록이 아니라 <b>현재 단가로 다시 잰</b> 값이다(USD)
 * @param rounds   턴에 속한 LLM 호출 건수. 턴 하나가 여러 라운드를 쓸 수 있다
 * @param durationSeconds 소요 시간(초). 제출 시각 − 첫 CODE 호출 시각. 제출 시각을 모르는 옛 기록은 null
 */
public record RankingEntry(
        int rank,
        Long attemptId,
        Long userId,
        String nickname,
        BigDecimal cost,
        long uncachedInputTokens,
        long cachedInputTokens,
        long outputTokens,
        int turns,
        int rounds,
        Instant submittedAt,
        Long durationSeconds
) {
}
