package com.promptstudio.ranking.domain;

import java.util.List;

/**
 * 문제 하나의 랭킹.
 *
 * @param totalCount 자격을 갖춘 제출 전체 수. entries는 그중 상위 일부다
 * @param myBest     요청자의 가장 좋은 줄. entries에 이미 들어 있어도 항상 채우고,
 *                   null은 "자격 있는 내 어템프트가 없다" 하나만 뜻한다
 */
public record ProblemRanking(
        Long problemId,
        long totalCount,
        List<RankingEntry> entries,
        RankingEntry myBest
) {
}
