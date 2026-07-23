import { runMockProblem, useMocks } from '../../mocks/api';
import { apiRequest } from '../../shared/api/apiClient';
import type { RunProblemRequest, RunProblemResponse } from './types';

export async function runProblem(
  problemId: number,
  request: RunProblemRequest,
): Promise<RunProblemResponse> {
  if (useMocks) return runMockProblem(problemId, request);

  return apiRequest<RunProblemResponse>(`/problems/${problemId}/run`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}
