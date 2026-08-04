package com.promptstudio.ranking.repository;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.ranking.domain.RankedPage;
import com.promptstudio.ranking.domain.RankingEntry;

import java.util.Optional;

public interface RankingQueryRepository {

    /**
     * 등수 순으로 상위 limit줄과 자격자 전체 수. 자격을 갖춘 제출이 없으면 빈 목록에 전체 수 0이다.
     *
     * <p>전체 수를 따로 묻는 메서드를 두지 않는 것이 의도다 — 등수를 매기는 쿼리가 이미 자격자 전 행을
     * 훑으므로, 나눠 물으면 같은 집계를 두 번 돌리게 된다.
     */
    RankedPage findTop(Long problemId, int limit);

    /** 주어진 소유자의 가장 좋은 줄. 자격을 갖춘 어템프트가 없으면 비어 있다. */
    Optional<RankingEntry> findBestOf(Long problemId, AttemptOwner owner);
}
