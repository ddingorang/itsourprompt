import { getMockProblemDetail, getMockProblems, useMocks } from '../../mocks/api';
import { apiRequest } from '../../shared/api/apiClient';
import type { ProblemDetail, ProblemListResponse } from './types';

export async function getProblems(): Promise<ProblemListResponse> {
  if (useMocks) return getMockProblems();
  return apiRequest<ProblemListResponse>('/problems');
}

/** 문제 상세 조회. 목 구현은 signal을 무시한다 — 개발 전용이라 취소가 필요 없다. */
export async function getProblemDetail(
  problemId: number,
  signal?: AbortSignal,
): Promise<ProblemDetail> {
  if (useMocks) return getMockProblemDetail(problemId);
  return apiRequest<ProblemDetail>(`/problems/${problemId}`, { signal });
}
