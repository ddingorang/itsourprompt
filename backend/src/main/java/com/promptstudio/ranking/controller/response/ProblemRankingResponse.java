package com.promptstudio.ranking.controller.response;

import com.promptstudio.ranking.domain.ProblemRanking;
import com.promptstudio.ranking.domain.RankingEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Schema(description = "문제 하나의 비용 랭킹")
public record ProblemRankingResponse(
        @Schema(description = "문제 ID", example = "3")
        Long problemId,

        @Schema(description = "랭킹에 든 제출 전체 수. entries는 그중 상위 일부다", example = "37")
        long totalCount,

        @Schema(description = "등수 순 상위 목록")
        List<RankingEntryResponse> entries,

        @Schema(description = "요청자의 가장 좋은 줄. entries에 이미 있어도 항상 채운다. "
                + "null은 '랭킹에 든 내 풀이가 없다'는 뜻이다")
        RankingEntryResponse myBest
) {

    /**
     * @param requesterUserId 요청자의 사용자 ID. 비어 있으면 어느 줄도 내 줄이 아니다(로그인하지 않은 요청)
     */
    public static ProblemRankingResponse from(ProblemRanking ranking, Optional<Long> requesterUserId) {
        RankingEntry myBest = ranking.myBest();

        return new ProblemRankingResponse(
                ranking.problemId(),
                ranking.totalCount(),
                ranking.entries().stream().map(entry -> RankingEntryResponse.from(entry, requesterUserId)).toList(),
                myBest == null ? null : RankingEntryResponse.from(myBest, requesterUserId)
        );
    }

    @Schema(description = "랭킹 한 줄. 어템프트 1건이 1줄이라 한 사람이 여러 줄을 차지할 수 있다")
    public record RankingEntryResponse(
            @Schema(description = "등수. 동점은 같은 등수를 받고 다음 등수는 건너뛴다", example = "3")
            int rank,

            @Schema(description = "이 줄의 어템프트 ID. 랭킹에서 해당 풀이·피드백 화면으로 이동할 때 쓴다", example = "42")
            Long attemptId,

            @Schema(description = "요청자 본인의 줄인지. 한 사람이 여러 줄을 차지할 수 있어 내 줄이 여럿일 수 "
                    + "있다. 상단 목록과 myBest의 중복 표시도 이 값으로 판단한다", example = "false")
            boolean mine,

            @Schema(description = "표시 이름. 닉네임이다", example = "프롬프트왕")
            String ownerLabel,

            @Schema(description = "현재 단가로 다시 잰 비용(USD)", example = "0.00300000")
            BigDecimal cost,

            @Schema(description = "캐시에 걸리지 않은 입력 토큰 합", example = "1500")
            long uncachedInputTokens,

            @Schema(description = "캐시에 걸린 입력 토큰 합", example = "400")
            long cachedInputTokens,

            @Schema(description = "출력 토큰 합", example = "500")
            long outputTokens,

            @Schema(description = "턴 수", example = "2")
            int turns,

            @Schema(description = "LLM 호출 수. 턴 하나가 여러 라운드를 쓸 수 있다", example = "4")
            int rounds,

            @Schema(description = "제출 시각(ISO-8601). 기존 기록은 null일 수 있음", example = "2026-08-03T05:10:32Z")
            Instant submittedAt,

            @Schema(description = "소요 시간(초). 제출 시각 − 첫 CODE 호출 시각. 실패한 코드 생성 호출도 "
                    + "시작으로 친다. 제출 시각을 모르는 옛 기록은 null", example = "252")
            Long durationSeconds
    ) {

        /**
         * attemptId는 모든 줄에 싣는다 — 제출된 어템프트는 이제 누구나 읽을 수 있어 ID를 실어도 새로
         * 열리는 것이 없고, 랭킹에서 남의 풀이로 가는 빠른 길이 된다. 주인의 신원(사용자 ID·세션 ID)은
         * 여전히 싣지 않는다.
         *
         * <p>내 줄인지는 소유자 신원으로 판정한다. 최고 기록과 견주면 상위에 오른 내 두 번째 줄이
         * 남의 줄과 구분되지 않는다 — 어템프트 1건이 1줄이라 한 사람이 여러 줄을 차지하기 때문이다.
         */
        public static RankingEntryResponse from(RankingEntry entry, Optional<Long> requesterUserId) {
            boolean mine = requesterUserId.filter(entry.userId()::equals).isPresent();

            return new RankingEntryResponse(
                    entry.rank(),
                    entry.attemptId(),
                    mine,
                    entry.nickname(),
                    entry.cost(),
                    entry.uncachedInputTokens(),
                    entry.cachedInputTokens(),
                    entry.outputTokens(),
                    entry.turns(),
                    entry.rounds(),
                    entry.submittedAt(),
                    entry.durationSeconds()
            );
        }
    }
}
