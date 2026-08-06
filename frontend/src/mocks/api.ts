/**
 * 백엔드 없이 화면을 개발하기 위한 목 응답. VITE_USE_MOCKS=true일 때만 쓰인다.
 *
 * 응답 형태는 백엔드 DTO(ProblemListResponse / ProblemDetailResponse /
 * AttemptResponse / FeedbackResponse)와 같게 유지한다 — 목이 실제 응답과
 * 어긋나면 목으로 만든 화면이 실서버에서 깨진다.
 */
import type {
  Attempt,
  AttemptFeedback,
  CodeRun,
  CodeRunListResponse,
  CodeRunTally,
  Turn,
} from '../features/attempt/types';
import type {
  ProblemDetail,
  ProblemListResponse,
} from '../features/problem/types';
import type { ProblemRanking, RankingEntry } from '../features/ranking/types';
import { ApiError, API_ERROR_CODES } from '../shared/api/apiClient';

export const useMocks = import.meta.env.VITE_USE_MOCKS === 'true';

const problemDetail: ProblemDetail = {
  id: 1,
  type: 'coding',
  title: '게시판 API 구현',
  specMd:
    '# 문제\n\n게시글 엔티티와 CRUD API 계층을 구현하세요. 코드는 직접 편집할 수 없고, 프롬프트로만 수정합니다.',
  files: [
    {
      path: 'build.gradle',
      content: `plugins {
  id 'java'
}

group = 'com.example'
version = '0.0.1'`,
    },
    {
      path: 'src/main/java/App.java',
      content: `package com.example.board;

public class App {
  public static void main(String[] args) {
    System.out.println("Board API skeleton");
  }
}`,
    },
    {
      path: 'src/test/AppTest.java',
      content: `package com.example.board;

class AppTest {
  // TODO: add tests
}`,
    },
  ],
};

/** 목 환경에서 어템프트 상태를 메모리에 유지한다(새로고침하면 초기화된다). */
const mockAttempts = new Map<number, Attempt>();
let nextMockAttemptId = 1;

interface MockCodeRunEntry {
  createdAt: string;
  readyAt: number;
  run: CodeRun;
}

const mockCodeRuns = new Map<number, MockCodeRunEntry[]>();

export async function getMockProblems(): Promise<ProblemListResponse> {
  await delay(250);
  return {
    problems: [
      { id: 1, language: 'java', title: '게시판 API 구현', type: 'coding' },
      { id: 2, language: 'java', title: 'Todo 입력 예외 처리', type: 'coding' },
      { id: 3, language: 'python', title: '사용자 프로필 컴포넌트', type: 'coding' },
      { id: 4, language: 'python', title: '상품 목록 필터링', type: 'coding' },
      { id: 5, language: 'java', title: '주문 상태 전이 검증', type: 'game' },
    ],
  };
}

/**
 * 목 문제는 100번 미만만 있다. 그 위는 실서버와 같은 404 + problem-not-found로
 * 실패시킨다 — 목만 성공하면 없는 문제를 가리키는 화면이 목에서만 다르게 보인다.
 */
function mockProblemNotFound(problemId: number): ApiError {
  return new ApiError(404, {
    code: API_ERROR_CODES.problemNotFound,
    message: `목 문제 ${problemId}를 찾을 수 없습니다.`,
  });
}

export async function getMockProblemDetail(problemId: number): Promise<ProblemDetail> {
  await delay(250);

  if (problemId >= 100) throw mockProblemNotFound(problemId);
  return { ...problemDetail, id: problemId };
}

const mockRankingNicknames = [
  '프롬프트왕',
  '토큰줍는사람',
  'tokenshaver',
  '한줄이면충분',
  'cold_start',
  'minimal_prompt',
  '세턴안에끝냄',
  '캐시장인',
];

/**
 * 랭킹 줄이 가리키는 목 어템프트 ID의 자리. 내 줄과 남의 줄을 다른 대역에 두어,
 * 어템프트 조회가 ID만 보고 주인(mine)을 되돌려 줄 수 있게 한다.
 */
const MY_RANKED_ATTEMPT_ID = 4100;
const RANKED_ATTEMPT_ID_BASE = 4200;

/**
 * 랭킹 한 줄을 만든다. 비용·토큰은 등수로 계산해 동점 줄이 같은 값을 갖게 한다 —
 * 등수와 비용이 어긋나면 표가 목에서만 이상해 보인다.
 */
function buildMockRankingEntry(
  rank: number,
  index: number,
  mineIndex: number | null,
): RankingEntry {
  const nickname = mockRankingNicknames[index % mockRankingNicknames.length];
  const mine = index === mineIndex;

  return {
    rank,
    // attemptId는 모든 줄에 온다 — 랭킹에 오른 제출은 모두 공개라 남의 줄에서도
    // 피드백으로 갈 수 있다. 등수는 동점으로 겹치므로 자리(index)로 ID를 가른다.
    attemptId: mine ? MY_RANKED_ATTEMPT_ID : RANKED_ATTEMPT_ID_BASE + index + 1,
    mine,
    ownerLabel: mine ? '내닉네임' : nickname,
    cost: 0.0012 + (rank - 1) * 0.00037,
    uncachedInputTokens: 1500 + (rank - 1) * 220,
    cachedInputTokens: 400 + (rank - 1) * 130,
    outputTokens: 500 + (rank - 1) * 90,
    turns: 1 + (rank % 4),
    rounds: 2 + (rank % 5),
    submittedAt: `2026-08-0${(rank % 3) + 1}T0${rank % 9}:1${rank % 9}:32Z`,
  };
}

function buildMockRanking(
  problemId: number,
  ranks: number[],
  mineIndex: number | null,
  totalCount = ranks.length,
): ProblemRanking {
  const entries = ranks.map((rank, index) =>
    buildMockRankingEntry(rank, index, mineIndex),
  );

  return {
    problemId,
    totalCount,
    entries,
    myBest: mineIndex === null ? null : (entries[mineIndex] ?? null),
  };
}

function sequentialRanks(count: number): number[] {
  return Array.from({ length: count }, (_, index) => index + 1);
}

/**
 * 손으로는 재현할 수 없는 랭킹 상태를 문제 ID로 갈라 둔다 —
 * FE에 테스트 프레임워크가 없어 목이 화면을 검증하는 유일한 수단이다.
 */
function buildMockRankingFor(problemId: number): ProblemRanking {
  switch (problemId) {
    // 정상 12줄, 내 줄이 상위 목록 안(3위)에 있다.
    case 1:
      return buildMockRanking(problemId, sequentialRanks(12), 2);
    // 동점 — 같은 등수가 반복되고 다음 등수는 건너뛴다.
    case 2:
      return buildMockRanking(problemId, [1, 1, 3, 4, 5, 5, 5, 8, 9, 10], 5);
    // limit(50)으로 잘린 상위 목록. 51위 이하는 볼 방법이 없다.
    case 3:
      return buildMockRanking(problemId, sequentialRanks(50), 46, 500);
    // 자격을 갖춘 내 어템프트가 없어 myBest가 비어 있다.
    case 4:
      return buildMockRanking(problemId, sequentialRanks(8), null);
    // 아직 아무도 통과하지 못한 문제.
    case 5:
      return buildMockRanking(problemId, [], null, 0);
    default:
      return buildMockRanking(problemId, sequentialRanks(6), null);
  }
}

export async function getMockProblemRanking(
  problemId: number,
  limit: number,
): Promise<ProblemRanking> {
  await delay(250);

  if (problemId >= 100) throw mockProblemNotFound(problemId);

  const ranking = buildMockRankingFor(problemId);
  return { ...ranking, entries: ranking.entries.slice(0, limit) };
}

export async function createMockAttempt(problemId: number): Promise<Attempt> {
  await delay(300);

  const attempt: Attempt = {
    id: nextMockAttemptId++,
    problemId,
    baseFiles: problemDetail.files,
    files: problemDetail.files,
    status: 'IN_PROGRESS',
    turns: [],
  };

  mockAttempts.set(attempt.id, attempt);
  return attempt;
}

/**
 * 없는 어템프트는 실서버와 같은 404 + attempt-not-found로 실패시킨다 — 화면이
 * code로 분기해 안내를 고르므로, 목이 맨 Error를 던지면 그 분기가 목에서만 빗나간다.
 */
function mockAttemptNotFound(attemptId: number): ApiError {
  return new ApiError(404, {
    code: API_ERROR_CODES.attemptNotFound,
    message: `목 어템프트 ${attemptId}를 찾을 수 없습니다.`,
  });
}

export async function getMockAttempt(attemptId: number): Promise<Attempt> {
  await delay(200);

  const attempt = mockAttempts.get(attemptId);
  if (!attempt) {
    throw mockAttemptNotFound(attemptId);
  }
  return attempt;
}

export async function addMockTurn(attemptId: number, prompt: string): Promise<Attempt> {
  await delay(800);

  const attempt = mockAttempts.get(attemptId);
  if (!attempt) {
    throw mockAttemptNotFound(attemptId);
  }

  const turnNumber = attempt.turns.length + 1;
  const turnUsage = {
    inputTokens: 2500 + (turnNumber - 1) * 320,
    uncachedInputTokens: 1500 + (turnNumber - 1) * 200,
    cachedInputTokens: 1000 + (turnNumber - 1) * 120,
    outputTokens: 500 + (turnNumber - 1) * 80,
    reasoningTokens: 120 + (turnNumber - 1) * 20,
    latencyMs: 260 + (turnNumber - 1) * 35,
    cost: 0.003 + (turnNumber - 1) * 0.0004,
  };
  const addedFile = {
    path: `src/main/java/Post${turnNumber}.java`,
    content: `package com.example.board;

// ${turnNumber}번째 턴에서 생성됨
public record Post${turnNumber}(Long id, String title, String content) {}`,
  };

  const turn: Turn = {
    prompt,
    aiResponse: `${turnNumber}번째 요청을 반영해 Post${turnNumber} 레코드를 추가했습니다.`,
    changedFiles: [
      {
        path: addedFile.path,
        changeType: 'ADDED',
        content: addedFile.content,
      },
    ],
    toolCalls: [
      { tool: 'list_files', path: null },
      { tool: 'read_file', path: 'src/main/java/App.java' },
      { tool: 'edit_file', path: addedFile.path },
    ],
    usage: turnUsage,
  };

  const updated: Attempt = {
    ...attempt,
    files: [...attempt.files, addedFile],
    turns: [...attempt.turns, turn],
    usage: {
      inputTokens: (attempt.usage?.inputTokens ?? 0) + turnUsage.inputTokens,
      uncachedInputTokens:
        (attempt.usage?.uncachedInputTokens ?? 0) + turnUsage.uncachedInputTokens,
      cachedInputTokens:
        (attempt.usage?.cachedInputTokens ?? 0) + turnUsage.cachedInputTokens,
      outputTokens: (attempt.usage?.outputTokens ?? 0) + turnUsage.outputTokens,
      reasoningTokens:
        (attempt.usage?.reasoningTokens ?? 0) + turnUsage.reasoningTokens,
      latencyMs: (attempt.usage?.latencyMs ?? 0) + turnUsage.latencyMs,
      cost: (attempt.usage?.cost ?? 0) + turnUsage.cost,
      rounds: (attempt.usage?.rounds ?? 0) + 1,
    },
  };

  mockAttempts.set(attemptId, updated);
  return updated;
}

function completeMockCodeRun(entry: MockCodeRunEntry): CodeRun {
  if (entry.run.status !== 'QUEUED' || Date.now() < entry.readyAt) {
    return entry.run;
  }

  entry.run = {
    ...entry.run,
    cases: [
      {
        className: 'PostTest',
        name: '게시글을_생성한다()',
        status: 'PASSED',
        message: null,
        durationMs: 12,
      },
      {
        className: 'PostTest',
        name: '존재하지_않는_게시글은_예외를_반환한다()',
        status: 'FAILED',
        message: 'expected: <404> but was: <200>',
        durationMs: 17,
      },
    ],
    durationMs: 1224,
    exitCode: 1,
    status: 'TEST_FAILED',
    stdout: 'JUnit Platform Suite\nPostTest: 1 passed, 1 failed',
    stderr: null,
  };
  return entry.run;
}

function tallyMockCases(run: CodeRun): CodeRunTally | null {
  if (run.cases.length === 0) return null;

  return run.cases.reduce<CodeRunTally>(
    (tally, testCase) => {
      tally.total += 1;
      if (testCase.status === 'PASSED') tally.passed += 1;
      else if (testCase.status === 'FAILED') tally.failed += 1;
      else if (testCase.status === 'ERROR') tally.error += 1;
      else tally.skipped += 1;
      return tally;
    },
    { error: 0, failed: 0, passed: 0, skipped: 0, total: 0 },
  );
}

export async function requestMockCodeRun(
  attemptId: number,
  turnOrdinal?: number,
): Promise<CodeRun> {
  await delay(200);

  const attempt = mockAttempts.get(attemptId);
  if (!attempt) throw mockAttemptNotFound(attemptId);

  const entries = mockCodeRuns.get(attemptId) ?? [];
  if (entries.some((entry) => completeMockCodeRun(entry).status === 'QUEUED')) {
    throw new ApiError(409, {
      code: API_ERROR_CODES.codeRunInProgress,
      message: '이미 진행 중인 코드 실행이 있습니다.',
    });
  }

  const run: CodeRun = {
    runId: crypto.randomUUID(),
    turnOrdinal:
      turnOrdinal ?? (attempt.turns.length > 0 ? attempt.turns.length - 1 : null),
    status: 'QUEUED',
    exitCode: null,
    stdout: null,
    stderr: null,
    durationMs: null,
    cases: [],
  };
  entries.unshift({ createdAt: new Date().toISOString(), readyAt: Date.now() + 1500, run });
  mockCodeRuns.set(attemptId, entries);
  return run;
}

export async function getMockCodeRuns(
  attemptId: number,
): Promise<CodeRunListResponse> {
  await delay(150);
  if (!mockAttempts.has(attemptId)) throw mockAttemptNotFound(attemptId);

  const entries = mockCodeRuns.get(attemptId) ?? [];
  return {
    runs: entries.map((entry) => {
      const run = completeMockCodeRun(entry);
      return {
        runId: run.runId,
        turnOrdinal: run.turnOrdinal,
        status: run.status,
        exitCode: run.exitCode,
        durationMs: run.durationMs,
        createdAt: entry.createdAt,
        finishedAt: run.status === 'QUEUED' ? null : new Date(entry.readyAt).toISOString(),
        tally: tallyMockCases(run),
      };
    }),
  };
}

export async function getMockCodeRun(
  attemptId: number,
  runId: string,
): Promise<CodeRun> {
  await delay(150);
  const entry = (mockCodeRuns.get(attemptId) ?? []).find(
    (candidate) => candidate.run.runId === runId,
  );
  if (!entry) {
    throw new ApiError(404, {
      code: API_ERROR_CODES.codeRunNotFound,
      message: '코드 실행 기록을 찾을 수 없습니다.',
    });
  }
  return completeMockCodeRun(entry);
}

/**
 * BE가 총평 뒤에 고정으로 붙이는 출처 한 줄. 모델이 아니라 코드가 붙이는 문장이라
 * 목에서도 문구를 그대로 쓴다.
 */
const PATTERN_SOURCE_NOTE =
  '\n\n---\n여기 쓴 용어는 AI Coding Dictionary에서 가져왔어요. https://aicodingdictionary.com';

export async function submitMockAttempt(attemptId: number): Promise<AttemptFeedback> {
  await delay(600);

  const attempt = mockAttempts.get(attemptId);
  const turns = attempt?.turns ?? [];

  mockAttempts.set(attemptId, {
    ...(attempt ?? {
      id: attemptId,
      problemId: problemDetail.id,
      baseFiles: problemDetail.files,
      files: problemDetail.files,
      turns: [],
      status: 'IN_PROGRESS',
    }),
    status: 'SUBMITTED',
  });

  const patternNames = turns.map((_, index) => mockPatternName(index + 1));

  return {
    turns: turns.map((turn, index) => ({
      turn: index + 1,
      feedbackMd: `## 관찰\n\n${index + 1}번째 프롬프트는 "${turn.prompt.slice(0, 30)}…" 형태로 요청했습니다.\n\n## 개선 제안\n\nHTTP 메서드와 경로, 요청·응답 형식을 함께 명시하면 의도가 더 정확히 전달됩니다.`,
      patternMd: mockPatternMd(index + 1, patternNames[index]),
    })),
    overallMd: `## 세션 총평\n\n총 ${turns.length}개의 턴으로 문제를 풀었습니다.\n\n초반 프롬프트에서 도메인 모델과 API 계층을 한 번에 요구하기보다, 단계를 나눠 요청하면 AI가 의도를 덜 추측합니다.`,
    patternOverallMd: mockPatternOverallMd(patternNames),
  };
}

/**
 * BE는 이름을 이 턴 프롬프트가 앞 턴에 바뀐 파일을 부르는지로 가른다. 목에는 대조할 프롬프트가
 * 없어 턴 번호로 흉내만 낸다.
 *
 * <p>첫 턴만은 흉내가 아니라 계약이다 — 앞 턴이 없어 BE도 두 이름 중 어느 쪽도 붙이지 못하므로
 * null이고, 그 턴은 이름 없이 앞선 결과가 없다고만 쓴다.
 */
type MockPatternName = 'vibe coding' | 'human review' | null;

function mockPatternName(turn: number): MockPatternName {
  if (turn === 1) {
    return null;
  }

  return turn % 2 === 0 ? 'vibe coding' : 'human review';
}

/**
 * 세션 이름은 턴에 붙은 이름을 세어 고른다. 이름을 셀 수 있는 턴이 하나도 없으면 이름을 붙이지
 * 않는다 — BE도 턴들이 한 방식으로 모이지 않으면 이름 대신 그 사실을 쓴다. 가져갈 기법은 세션
 * 이름과 늘 다른 용어여야 한다.
 */
function mockPatternOverallMd(names: MockPatternName[]): string {
  // 첫 턴은 앞선 결과가 없어 이름이 없으므로 분모에서도 뺀다 — 짚었는지를 셀 수 없는 턴이다.
  const namedTurns = names.filter((name) => name !== null).length;
  const vibeTurns = names.filter((name) => name === 'vibe coding').length;

  if (namedTurns === 0) {
    return `### 이번 세션의 이름\n\n이름을 잴 턴이 없어 이번 세션에는 이름을 못 붙였어요. 턴을 하나 더 쌓으면 앞 턴의 결과를 어떻게 다루셨는지 드러나요.\n\n### 다음 세션에 가져갈 것\n\n\`human review\` — 사람이 바뀐 코드를 직접 읽고 판단하는 기법이에요. 프롬프트를 쓰기 전에 방금 바뀐 파일을 열고, 고칠 곳을 파일 이름으로 부르세요.${PATTERN_SOURCE_NOTE}`;
  }

  if (vibeTurns * 2 >= namedTurns) {
    return `### 이번 세션의 이름\n\n\`vibe coding\` — AI가 낸 코드를 읽지 않고 받는 방식이에요. 이름이 붙은 ${namedTurns}턴 중 ${vibeTurns}턴에서 앞 턴이 바꾼 레코드를 프롬프트가 안 짚었어요. 필드 이름과 타입은 AI가 정한 대로 남았어요.\n\n### 다음 세션에 가져갈 것\n\n\`human review\` — 사람이 바뀐 코드를 직접 읽고 판단하는 기법이에요. 프롬프트를 쓰기 전에 방금 바뀐 파일을 열고, 고칠 곳을 파일 이름으로 부르세요.${PATTERN_SOURCE_NOTE}`;
  }

  return `### 이번 세션의 이름\n\n\`human review\` — 사람이 바뀐 코드를 직접 읽고 판단하는 방식이에요. 이름이 붙은 ${namedTurns}턴 중 ${namedTurns - vibeTurns}턴에서 앞 턴이 바꾼 파일을 프롬프트가 다시 불렀어요. AI가 정한 것을 그대로 두지 않으셨어요.\n\n### 다음 세션에 가져갈 것\n\n\`design concept\` — 무엇을 만들지 사람과 AI가 미리 맞춘 그림이에요. 다음 세션 첫 프롬프트에 어떤 파일을 어떻게 바꿀지 한 문장으로 먼저 적어 보세요.${PATTERN_SOURCE_NOTE}`;
}

/**
 * 이름이 없는 첫 턴은 제목 줄도 `### 쓸 기법` 절도 쓰지 않는다 — BE는 이름이 비면 제목을 안 붙이고
 * (`PatternPrompts.renderTurn`), 기법 절은 `vibe coding` 턴에만 붙인다. 목이 절을 채우면 화면이
 * 실서버보다 항상 길어 보인다.
 */
function mockPatternMd(turn: number, name: MockPatternName): string {
  if (name === null) {
    return '첫 턴이라 앞선 결과가 없어요.';
  }

  if (name === 'vibe coding') {
    return `### 이 턴의 이름\n\n\`vibe coding\` — AI가 낸 코드를 읽지 않고 받는 방식이에요. 턴 ${turn - 1}에서 AI가 Post${turn - 1}.java를 새로 만들었는데, 이 턴 프롬프트에 Post${turn - 1}이 안 나와요. AI가 정한 필드 이름을 그대로 두셨어요.\n\n### 쓸 기법\n\n\`human review\` — 사람이 바뀐 코드를 읽고 판단하는 기법이에요. 이 턴 프롬프트를 쓰기 전에 Post${turn - 1}.java를 열어 봤다면, 필드가 문제에서 요구한 것과 맞는지 확인할 수 있었어요.`;
  }

  return `### 이 턴의 이름\n\n\`human review\` — 사람이 바뀐 코드를 읽고 판단하는 방식이에요. 턴 ${turn - 1}에서 AI가 Post${turn - 1}.java를 새로 만들었고, 이 턴 프롬프트가 Post${turn - 1}을 다시 불러 고칠 곳을 짚었어요. AI가 정한 것을 그대로 두지 않으셨어요.`;
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}
