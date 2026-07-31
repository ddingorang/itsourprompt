/**
 * 진행 중인 어템프트 ID를 문제별로 보관한다.
 *
 * 어템프트 생성(POST /api/attempts)은 서버에 상태를 만드는 작업이라, 새로고침할
 * 때마다 새 어템프트를 만들면 진행 중이던 턴 기록이 버려진다. 그래서 문제별로
 * 어템프트 ID만 sessionStorage에 남겨 두고, 다시 들어오면 GET /api/attempts/{id}로
 * 상태를 복원한다.
 *
 * 파일 내용이나 턴 기록 자체는 저장하지 않는다 — 서버가 가진 것이 정본이고,
 * 클라이언트에 복제해 두면 두 값이 어긋날 수 있다.
 */

const ATTEMPT_KEY_PREFIX = 'promptStudio.attempt.';

function attemptKey(problemId: number): string {
  return `${ATTEMPT_KEY_PREFIX}${problemId}`;
}

export function saveAttemptId(problemId: number, attemptId: number): void {
  try {
    sessionStorage.setItem(attemptKey(problemId), String(attemptId));
  } catch {
    // 시크릿 모드 등 sessionStorage를 못 쓰는 환경에서는 복원만 포기한다.
  }
}

export function getAttemptId(problemId: number): number | null {
  try {
    const saved = sessionStorage.getItem(attemptKey(problemId));
    if (!saved) return null;

    const attemptId = Number(saved);
    return Number.isInteger(attemptId) && attemptId > 0 ? attemptId : null;
  } catch {
    return null;
  }
}

/** 어템프트가 사라졌거나(404) 제출이 끝나 더 이어갈 수 없을 때 정리한다. */
export function clearAttemptId(problemId: number): void {
  try {
    sessionStorage.removeItem(attemptKey(problemId));
  } catch {
    // 무시한다.
  }
}
