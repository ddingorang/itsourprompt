import { submitMockFeedback, useMocks } from '../../mocks/api';
import { apiRequest } from '../../shared/api/apiClient';
import type { FeedbackResponse, SubmitFeedbackRequest } from './types';

export async function submitFeedback(
  problemId: number,
  request: SubmitFeedbackRequest,
): Promise<FeedbackResponse> {
  if (useMocks) return submitMockFeedback(problemId, request);

  return apiRequest<FeedbackResponse>(`/problems/${problemId}/submit`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}
