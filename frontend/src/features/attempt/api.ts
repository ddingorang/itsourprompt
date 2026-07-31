import {
  createMockAttempt,
  addMockTurn,
  getMockAttempt,
  submitMockAttempt,
  useMocks,
} from '../../mocks/api';
import { apiRequest } from '../../shared/api/apiClient';
import type { Attempt, AttemptFeedback } from './types';

/**
 * 어템프트 API 호출 모음.
 *
 * AI를 호출하는 생성/턴 추가에는 Idempotency-Key를 붙일 수 있다. 네트워크가
 * 끊겨 재시도할 때 같은 키를 보내면 백엔드가 AI를 다시 부르지 않고 기존 결과를
 * 반환하므로, 비싼 AI 호출이 중복되지 않는다.
 */

/** 새 어템프트 시작. 문제 스켈레톤 파일로 초기화된다. */
export async function createAttempt(
  problemId: number,
  idempotencyKey?: string,
): Promise<Attempt> {
  if (useMocks) return createMockAttempt(problemId);

  return apiRequest<Attempt>('/attempts', {
    body: JSON.stringify({ problemId }),
    idempotencyKey,
    method: 'POST',
  });
}

/** 어템프트 조회. 새로고침 후 진행 상태를 복원하는 데 쓴다. */
export async function getAttempt(attemptId: number): Promise<Attempt> {
  if (useMocks) return getMockAttempt(attemptId);

  return apiRequest<Attempt>(`/attempts/${attemptId}`);
}

/** 턴 추가. 서버가 이전 대화 이력을 기억하므로 이번 프롬프트만 보내면 된다. */
export async function addTurn(
  attemptId: number,
  prompt: string,
  idempotencyKey?: string,
): Promise<Attempt> {
  if (useMocks) return addMockTurn(attemptId, prompt);

  return apiRequest<Attempt>(`/attempts/${attemptId}/turns`, {
    body: JSON.stringify({ prompt }),
    idempotencyKey,
    method: 'POST',
  });
}

/**
 * 어템프트 제출. 턴별 + 전체 피드백을 생성해 저장하고 어템프트를 종료한다.
 * 이미 제출된 어템프트를 다시 제출하면 AI 재호출 없이 저장된 피드백을 반환한다.
 */
export async function submitAttempt(attemptId: number): Promise<AttemptFeedback> {
  if (useMocks) return submitMockAttempt(attemptId);

  return apiRequest<AttemptFeedback>(`/attempts/${attemptId}/submit`, {
    method: 'POST',
  });
}

/** 저장된 피드백 조회. 제출 전에는 404(feedback-not-found)로 실패한다. */
export async function getAttemptFeedback(attemptId: number): Promise<AttemptFeedback> {
  if (useMocks) return submitMockAttempt(attemptId);

  return apiRequest<AttemptFeedback>(`/attempts/${attemptId}/feedback`);
}
