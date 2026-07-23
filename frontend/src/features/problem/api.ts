import { getMockProblemDetail, getMockProblems, useMocks } from '../../mocks/api';
import { apiRequest } from '../../shared/api/apiClient';
import type { ProblemDetail, ProblemListResponse } from './types';

export async function getProblems(): Promise<ProblemListResponse> {
  if (useMocks) return getMockProblems();
  return apiRequest<ProblemListResponse>('/problems');
}

export async function getProblemDetail(problemId: number): Promise<ProblemDetail> {
  if (useMocks) return getMockProblemDetail(problemId);
  return apiRequest<ProblemDetail>(`/problems/${problemId}`);
}
