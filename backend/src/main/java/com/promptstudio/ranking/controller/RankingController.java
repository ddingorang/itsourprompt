package com.promptstudio.ranking.controller;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.global.exception.ApiErrorResponse;
import com.promptstudio.global.security.AppUserDetails;
import com.promptstudio.guest.AttemptOwnerResolver;
import com.promptstudio.ranking.controller.response.ProblemRankingResponse;
import com.promptstudio.ranking.exception.RankingLimitOutOfRangeException;
import com.promptstudio.ranking.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 문제별 비용 랭킹. 로그인 없이 누구나 볼 수 있고 조회만 한다.
 *
 * <p>소유자는 있으면 쓰고 없으면 만들지 않는다 — 랭킹만 구경한 방문자에게 게스트 세션과 쿠키가
 * 생기면 안 되기 때문이다(GuestSessionFilter가 이 경로에 걸리지 않는 것도 같은 이유다).
 */
@RestController
@RequestMapping("/api/problems")
@Tag(name = "Ranking", description = "문제별 비용 랭킹 API")
public class RankingController {

    private final RankingService rankingService;
    private final AttemptOwnerResolver attemptOwnerResolver;

    public RankingController(RankingService rankingService, AttemptOwnerResolver attemptOwnerResolver) {
        this.rankingService = rankingService;
        this.attemptOwnerResolver = attemptOwnerResolver;
    }

    @GetMapping("/{problemId}/ranking")
    @Operation(
            summary = "문제 비용 랭킹 조회",
            description = "마지막 턴의 코드가 채점을 통과한 제출을 비용 오름차순으로 반환합니다. "
                    + "비용은 저장된 값이 아니라 현재 단가로 다시 계산한 값이라 모두가 같은 자로 재어집니다. "
                    + "로그인 세션이나 게스트 쿠키가 있으면 myBest에 요청자의 최고 기록을 함께 담습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ProblemRankingResponse.class))),
            @ApiResponse(responseCode = "400", description = "limit이 허용 범위를 벗어남",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "문제를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProblemRankingResponse getRanking(
            @PathVariable("problemId") Long problemId,
            @Parameter(description = "가져올 줄 수(1~50)")
            @RequestParam(name = "limit", defaultValue = "" + RankingService.DEFAULT_LIMIT) int limit,
            @AuthenticationPrincipal AppUserDetails principal,
            HttpServletRequest request
    ) {
        Optional<Long> userId = attemptOwnerResolver.resolveExisting(principal, request)
                .filter(AttemptOwner::isUser)
                .map(AttemptOwner::userId);

        return ProblemRankingResponse.from(rankingService.getRanking(problemId, limit, userId), userId);
    }
}
