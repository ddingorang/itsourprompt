/** GET /api/me/attempts 응답의 제출 완료 어템프트 한 건. */
export interface SubmittedAttempt {
  /** 피드백 조회에 사용하는 어템프트 ID. */
  attemptId: number;
  /** 문제 다시 풀기에 사용하는 문제 ID. */
  problemId: number;
  problemTitle: string;
  /** 제출 완료 시각(ISO-8601). 이전 기록은 null일 수 있다. */
  submittedAt: string | null;
}
