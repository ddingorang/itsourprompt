package com.promptstudio.ranking.domain;

import java.util.List;

/**
 * 랭킹 상위 한 쪽과, 그 쪽이 잘라낸 전체 수.
 *
 * <p>둘을 함께 돌려주는 이유는 등수를 매기는 쿼리가 어차피 자격자 전 행을 훑기 때문이다. 전체 수를
 * 따로 물으면 같은 집계를 한 번 더 돌리게 된다.
 *
 * @param totalCount 자격을 갖춘 제출 전체 수. entries는 그중 상위 일부다
 */
public record RankedPage(
        List<RankingEntry> entries,
        long totalCount
) {
}
