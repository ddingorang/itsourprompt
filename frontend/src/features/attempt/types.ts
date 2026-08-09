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
  inputTokens: number | null;
  uncachedInputTokens?: number | null;
  cachedInputTokens: number | null;
  outputTokens: number | null;
  reasoningTokens: number | null;
  latencyMs?: number | null;
  cost: number | null;
}

/** 어템프트 전체 사용량. rounds는 전체 LLM 호출 횟수다. */
export interface AttemptTokenUsage extends TokenUsage {
  rounds?: number;
}

/** 어템프트의 한 턴 기록. */
export interface Turn {
  prompt: string;
  aiResponse: string;
  changedFiles: ChangedFile[];
  toolCalls: ToolCall[];
  usage?: TokenUsage | null;
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
  /**
   * 주인의 표시 이름. 로그인 사용자는 닉네임, 게스트는 세션 UUID 앞 네 자다 —
   * 응답만으로는 둘을 구분할 수 없으므로 `게스트` 같은 접두어는 붙이지 않는다.
   *
   * null이 되는 경우가 둘이다. **생성(POST /api/attempts) 응답은 항상 null이다** —
   * 그 경로는 방금 쓴 엔티티로 응답을 만들어 닉네임을 조인하지 않는다(턴 추가와
   * 조회는 채워 준다). 그래서 생성 직후 화면에서 이름을 그리면 로그인 사용자에게도
   * 빈칸이 나온다. 나머지 하나는 **소유자 없는 과거 기록** — 소유자 컬럼이 생기기
   * 전에 쌓인 행이라 주인을 알 수 없다.
   *
   * 즉 조회 응답이라고 해서 값이 있다고 단정할 수 없다.
   */
  ownerLabel: string | null;
  /**
   * 요청자 본인의 어템프트인지. **현재 백엔드 AttemptResponse는 이 필드를 싣지 않아
   * undefined가 온다** — apiRequest는 응답을 검증 없이 캐스팅하므로 타입만 믿으면
   * 모든 풀이가 남의 것으로 보인다. 값이 없으면 "모른다"로 다루고, 남의 것이라고
   * 명시(false)될 때만 잠근다.
   */
  mine?: boolean;
  usage?: AttemptTokenUsage | null;
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
  /** 그 턴에 일한 방식에 이름을 붙인 두 번째 피드백. */
  patternMd: string | null;
}

/**
 * 제출 결과 피드백.
 * turns는 턴별 피드백 도입 이전에 제출된 어템프트에서는 빈 배열일 수 있다.
 *
 * pattern 피드백은 두 자리(patternMd / patternOverallMd)를 한 번의 제출에서 함께 만든다 —
 * pattern 도입 전에 제출된 어템프트는 두 필드가 함께 null이므로, 화면은 자리마다 따로
 * 판정하지 않고 한 번만 보면 된다.
 */
export interface AttemptFeedback {
  turns: TurnFeedback[];
  overallMd: string;
  patternOverallMd: string | null;
  /** 걸리는 습관이 없는 세션이면 null이다. 그때는 아무것도 그리지 않는다. */
  carry: CarryLine | null;
}

/**
 * 다음 문제의 상시 지시 파일에 붙여넣을 규칙 한 줄.
 *
 * 두 렌즈와 다르다 — 저 둘은 LLM이 쓴 문장이고 이건 어템프트를 읽은 순수 계산의 결과다.
 * 읽는 사람도 다르다. 렌즈는 사용자에게 말하고, rule은 사용자의 AI에게 할 지시다.
 * 그래서 문단이 아니라 복사해 갈 물건으로 그린다.
 */
export interface CarryLine {
  /** 이 줄을 고른 신호의 키. 화면에 쓰지 않는다. */
  signal: string;
  /**
   * 지시 파일에 그대로 붙여넣을 한 줄. 도구 중립이라 파일 이름이 들어 있지 않다.
   *
   * 다른 피드백 필드와 달리 Markdown이 아니다 — 사용자가 그대로 복사해 가므로 화면에 보이는
   * 것과 클립보드에 담기는 것이 같아야 한다. Markdown으로 렌더하지 말 것.
   */
  rule: string;
  /** 왜 이 줄인지. BE가 센 값으로 조립하는 평문 한 문장이다. */
  reason: string;
}

export type CodeRunStatus =
  | 'QUEUED'
  | 'SUCCEEDED'
  | 'COMPILE_ERROR'
  | 'TEST_FAILED'
  | 'RUNTIME_ERROR'
  | 'TIMEOUT'
  | 'RUNNER_ERROR';

export type CodeRunCaseStatus = 'PASSED' | 'FAILED' | 'ERROR' | 'SKIPPED';

export interface CodeRunCase {
  className: string | null;
  name: string;
  status: CodeRunCaseStatus;
  message: string | null;
  durationMs: number | null;
}

export interface CodeRun {
  runId: string;
  turnOrdinal: number | null;
  status: CodeRunStatus;
  exitCode: number | null;
  stdout: string | null;
  stderr: string | null;
  durationMs: number | null;
  cases: CodeRunCase[];
}

export interface CodeRunTally {
  total: number;
  passed: number;
  failed: number;
  error: number;
  skipped: number;
}

export interface CodeRunSummary {
  runId: string;
  turnOrdinal: number | null;
  status: CodeRunStatus;
  exitCode: number | null;
  durationMs: number | null;
  createdAt: string;
  finishedAt: string | null;
  tally: CodeRunTally | null;
}

export interface CodeRunListResponse {
  runs: CodeRunSummary[];
}
