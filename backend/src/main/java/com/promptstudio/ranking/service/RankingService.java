package com.promptstudio.ranking.service;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.ranking.domain.ProblemRanking;
import com.promptstudio.ranking.domain.RankedPage;
import com.promptstudio.ranking.domain.RankingEntry;
import com.promptstudio.ranking.exception.RankingLimitOutOfRangeException;
import com.promptstudio.ranking.repository.RankingQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RankingService {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 50;

    private final ProblemRepository problemRepository;
    private final RankingQueryRepository rankingQueryRepository;

    public RankingService(ProblemRepository problemRepository, RankingQueryRepository rankingQueryRepository) {
        this.problemRepository = problemRepository;
        this.rankingQueryRepository = rankingQueryRepository;
    }

    /**
     * 문제 하나의 랭킹.
     *
     * <p>비활성 문제도 그대로 보여준다 — 이미 푼 사람들의 기록은 문제가 목록에서 내려갔다고 사라지지 않는다.
     *
     * @param owner 요청자. 비어 있으면 내 순위를 계산하지 않는다(로그인도 게스트 쿠키도 없는 방문자)
     */
    @Transactional(readOnly = true)
    public ProblemRanking getRanking(Long problemId, int limit, Optional<AttemptOwner> owner) {
        // 값 검사가 먼저다. 못 쓸 요청 때문에 DB를 두드릴 이유가 없고, 잘못된 limit이 없는 문제로 가면
        // 400과 404 중 뭐가 나올지가 순서에 좌우된다.
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new RankingLimitOutOfRangeException(limit, MIN_LIMIT, MAX_LIMIT);
        }

        // 있는지만 물어본다 — findById는 문제 명세(spec_md) 전문까지 끌고 온다.
        if (!problemRepository.existsById(problemId)) {
            throw new ProblemNotFoundException(problemId);
        }

        RankedPage page = rankingQueryRepository.findTop(problemId, limit);
        RankingEntry myBest = owner
                .flatMap(it -> rankingQueryRepository.findBestOf(problemId, it))
                .orElse(null);

        return new ProblemRanking(problemId, page.totalCount(), page.entries(), myBest);
    }
}
