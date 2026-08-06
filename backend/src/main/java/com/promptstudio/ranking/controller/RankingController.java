package com.promptstudio.ranking.controller;

import com.promptstudio.global.exception.ApiErrorResponse;
import com.promptstudio.global.security.AppUserDetails;
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
 * <p>요청자는 로그인 세션에서만 읽고 게스트 쿠키는 아예 보지 않는다 — 랭킹에 드는 것은 로그인
 * 사용자의 제출뿐이라 게스트 신원을 알아봐야 쓸 데가 없다. 덕분에 랭킹만 구경한 방문자에게
 * 게스트 세션이나 쿠키가 생길 여지도 없다(GuestSessionFilter가 이 경로에 걸리지 않는 것과 같다).
 */
@RestController
@RequestMapping("/api/problems")
@Tag(name = "Ranking", description = "문제별 비용 랭킹 API")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/{problemId}/ranking")
    @Operation(
            summary = "문제 비용 랭킹 조회",
            description = "마지막 턴의 코드가 채점을 통과한 제출을 비용 오름차순으로 반환합니다. "
                    + "비용은 저장된 값이 아니라 현재 단가로 다시 계산한 값이라 모두가 같은 자로 재어집니다. "
                    + "로그인한 요청에만 myBest를 담습니다. 게스트 제출은 랭킹에 들지 않습니다."
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
            @AuthenticationPrincipal AppUserDetails principal
    ) {
        Optional<Long> userId = Optional.ofNullable(principal).map(AppUserDetails::id);

        return ProblemRankingResponse.from(rankingService.getRanking(problemId, limit, userId), userId);
    }
}
