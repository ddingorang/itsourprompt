import { apiRequest } from '../../shared/api/apiClient';
import type { SubmittedAttempt } from './types';

/** 로그인 사용자의 제출 완료 어템프트 목록을 최신순으로 조회한다. */
export async function getMySubmittedAttempts(
  signal?: AbortSignal,
): Promise<SubmittedAttempt[]> {
  return apiRequest<SubmittedAttempt[]>('/me/attempts', { signal });
}
