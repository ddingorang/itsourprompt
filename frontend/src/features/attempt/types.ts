/**
 * 어템프트 도메인 타입. 백엔드 AttemptResponse / FeedbackResponse와 1:1 대응한다.
 *
 * 백엔드는 "문제를 한 번 실행"하는 무상태 모델이 아니라, 어템프트 하나에
 * 여러 턴을 누적하는 상태 모델이다:
 *
 *   POST /api/attempts            → 스켈레톤으로 초기화된 어템프트 생성
 *   POST /api/attempts/{id}/turns → 프롬프트 1턴 추가(이전 대화 이력을 서버가 기억)
 *   POST /api/attempts/{id}/submit→ 턴별 + 전체 피드백 생성 후 어템프트 종료
 *
 * 즉 대화 이력을 클라이언트가 모아 보낼 필요가 없다 — 서버가 누적한다.
 */
import type { RepositoryFile } from '../problem/types';

export type ChangeType = 'ADDED' | 'MODIFIED' | 'DELETED';

/** 어템프트 진행 상태. SUBMITTED가 되면 더 이상 턴을 추가할 수 없다. */
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED';

/** AI가 한 턴에서 호출한 툴. path는 대상이 없는 툴(list_files)이면 null이다. */
export interface ToolCall {
  tool: 'list_files' | 'read_file' | 'edit_file' | (string & {});
  path: string | null;
}

/** 턴에서 변경된 파일. DELETED인 파일은 content가 없다. */
export interface ChangedFile {
  path: string;
  changeType: ChangeType;
  content: string | null;
}

/** 한 번의 프롬프트 실행에서 발생한 LLM 사용량. */
export interface TokenUsage {
  inputTokens: number;
  uncachedInputTokens: number;
  cachedInputTokens: number;
  outputTokens: number;
  reasoningTokens: number;
  latencyMs: number;
  cost: number;
}

/** 어템프트 전체 사용량. rounds는 전체 LLM 호출 횟수다. */
export interface AttemptTokenUsage extends TokenUsage {
  rounds: number;
}

/** 어템프트의 한 턴 기록. */
export interface Turn {
  prompt: string;
  aiResponse: string;
  changedFiles: ChangedFile[];
  toolCalls: ToolCall[];
  usage?: TokenUsage;
}

/** 어템프트 전체 상태. 생성/조회/턴 추가가 모두 이 형태를 반환한다. */
export interface Attempt {
  id: number;
  problemId: number;
  /** 어템프트를 시작한 문제 스켈레톤 원본. 턴이 진행돼도 바뀌지 않는다. */
  baseFiles: RepositoryFile[];
  /** 현재 프로젝트 파일 전체. */
  files: RepositoryFile[];
  turns: Turn[];
  status: AttemptStatus;
  usage?: AttemptTokenUsage;
}

export interface CreateAttemptRequest {
  problemId: number;
}

export interface TurnRequest {
  prompt: string;
}

/** 한 턴의 프롬프트에 대한 피드백. turn은 1부터 시작한다. */
export interface TurnFeedback {
  turn: number;
  feedbackMd: string;
}

/**
 * 제출 결과 피드백.
 * turns는 턴별 피드백 도입 이전에 제출된 어템프트에서는 빈 배열일 수 있다.
 */
export interface AttemptFeedback {
  turns: TurnFeedback[];
  overallMd: string;
}
