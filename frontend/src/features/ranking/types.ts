export type RankingOwnerType = 'USER' | 'GUEST';

export interface RankingEntry {
  /** 등수. 동점은 같은 값을 받고 다음 등수는 건너뛴다(1, 1, 3). */
  rank: number;
  /** 내 줄일 때만 채워진다. 남의 줄은 null이라 피드백 링크를 걸 수 없다. */
  attemptId: number | null;
  mine: boolean;
  ownerType: RankingOwnerType;
  /** USER는 닉네임, GUEST는 세션 UUID 앞 네 자. 접두어는 화면이 붙인다. */
  ownerLabel: string;
  /**
   * 현재 단가로 다시 잰 비용(USD, 소수점 8자리).
   * JSON.parse가 0.00300000을 0.003으로 정규화하므로 자릿수는 화면이 만든다.
   */
  cost: number;
  uncachedInputTokens: number;
  cachedInputTokens: number;
  outputTokens: number;
  turns: number;
  /**
   * LLM 호출 수. 턴 하나가 여러 라운드를 쓸 수 있다.
   *
   * 화면에는 그리지 않는다 — 사용자가 조절하는 것은 턴이고 라운드는 AI가 정하므로,
   * 나란히 놓으면 자기가 못 바꾸는 숫자로 등수를 읽게 된다. 응답에는 오므로 남겨 둔다.
   */
  rounds: number;
  submittedAt: string | null;
}

export interface ProblemRanking {
  problemId: number;
  /** 랭킹에 든 제출 전체 수. entries는 그중 상위 일부다. */
  totalCount: number;
  entries: RankingEntry[];
  /** 요청자의 가장 좋은 줄. 상위 목록에 이미 있어도 항상 채워진다. */
  myBest: RankingEntry | null;
}
