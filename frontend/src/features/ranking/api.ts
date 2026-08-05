import { getMockProblemRanking, useMocks } from '../../mocks/api';
import { apiRequest } from '../../shared/api/apiClient';
import type { ProblemRanking } from './types';

/**
 * 문제별 비용 랭킹 조회. 목 구현은 signal을 무시한다 — 개발 전용이라 취소가 필요 없다.
 *
 * 인증은 필요 없지만 세션·게스트 쿠키가 함께 가면 myBest가 채워진다
 * (apiRequest가 credentials를 항상 싣는다).
 */
export async function getProblemRanking(
  problemId: number,
  limit: number,
  signal?: AbortSignal,
): Promise<ProblemRanking> {
  if (useMocks) return getMockProblemRanking(problemId, limit);
  return apiRequest<ProblemRanking>(
    `/problems/${problemId}/ranking?limit=${limit}`,
    { signal },
  );
}
