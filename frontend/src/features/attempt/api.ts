import {
  createMockAttempt,
  addMockTurn,
  getMockAttempt,
  getMockCodeRun,
  getMockCodeRuns,
  requestMockCodeRun,
  submitMockAttempt,
  useMocks,
} from '../../mocks/api';
import { apiRequest } from '../../shared/api/apiClient';
import type {
  Attempt,
  AttemptFeedback,
  CodeRun,
  CodeRunListResponse,
} from './types';

/**
 * 어템프트 API 호출 모음.
 *
 * 턴 추가에는 Idempotency-Key를 붙인다. 네트워크가 끊겨 재시도할 때 같은 키를
 * 보내면 백엔드가 AI를 다시 부르지 않고 기존 결과를 반환하므로, 비싼 AI 호출이
 * 중복되지 않는다.
 *
 * 조회 함수는 AbortSignal을 받아 화면이 지난 요청을 취소할 수 있게 한다. 목 구현은
 * signal을 무시한다 — 개발 전용이라 취소가 되지 않아도 화면이 깨지지 않는다.
 */

/**
 * 새 어템프트 시작. 문제 스켈레톤 파일로 초기화된다.
 *
 * 백엔드는 생성에도 Idempotency-Key를 받지만 일부러 넘기지 않는다 — 키는
 * 엔드포인트·어템프트와 무관하게 전역으로 조회되므로, 생성에 쓴 키가 턴 추가로
 * 새어 들어가면 턴이 붙지 않고 어템프트 상태만 되돌아온다. 생성은 AI를 부르지
 * 않아 중복 호출 비용도 없다.
 */
export async function createAttempt(problemId: number): Promise<Attempt> {
  if (useMocks) return createMockAttempt(problemId);

  return apiRequest<Attempt>('/attempts', {
    body: JSON.stringify({ problemId }),
    method: 'POST',
  });
}

/** 어템프트 조회. 주소가 가리키는 어템프트 상태를 읽어 오는 데 쓴다. */
export async function getAttempt(
  attemptId: number,
  signal?: AbortSignal,
): Promise<Attempt> {
  if (useMocks) return getMockAttempt(attemptId);

  return apiRequest<Attempt>(`/attempts/${attemptId}`, { signal });
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
export async function getAttemptFeedback(
  attemptId: number,
  signal?: AbortSignal,
): Promise<AttemptFeedback> {
  if (useMocks) return submitMockAttempt(attemptId);

  return apiRequest<AttemptFeedback>(`/attempts/${attemptId}/feedback`, { signal });
}

/** 현재 어템프트의 마지막 턴 코드 실행을 요청한다. */
export async function requestCodeRun(attemptId: number): Promise<CodeRun> {
  if (useMocks) return requestMockCodeRun(attemptId);

  return apiRequest<CodeRun>(`/attempts/${attemptId}/runs`, {
    method: 'POST',
  });
}

/** 지정한 0-based 턴의 코드 실행을 요청한다. */
export async function requestTurnCodeRun(
  attemptId: number,
  turnOrdinal: number,
): Promise<CodeRun> {
  if (useMocks) return requestMockCodeRun(attemptId, turnOrdinal);

  return apiRequest<CodeRun>(
    `/attempts/${attemptId}/turns/${turnOrdinal}/runs`,
    { method: 'POST' },
  );
}

/** 실행 이력을 최근순으로 조회한다. */
export async function getCodeRuns(
  attemptId: number,
  signal?: AbortSignal,
): Promise<CodeRunListResponse> {
  if (useMocks) return getMockCodeRuns(attemptId);

  return apiRequest<CodeRunListResponse>(`/attempts/${attemptId}/runs`, {
    signal,
  });
}

/** 실행 한 건의 상세 결과와 테스트 케이스를 조회한다. */
export async function getCodeRun(
  attemptId: number,
  runId: string,
  signal?: AbortSignal,
): Promise<CodeRun> {
  if (useMocks) return getMockCodeRun(attemptId, runId);

  return apiRequest<CodeRun>(`/attempts/${attemptId}/runs/${runId}`, {
    signal,
  });
}
