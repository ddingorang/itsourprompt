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
<<<<<<< HEAD
=======
import type {
  ProblemRanking,
  RankingEntry,
  RankingOwnerType,
} from '../features/ranking/types';
>>>>>>> 3b1e1afdf2a7e0bcbeaf4a55469af2bcf0b9d371
import { ApiError, API_ERROR_CODES } from '../shared/api/apiClient';

export const useMocks = import.meta.env.VITE_USE_MOCKS === 'true';

const problemDetail: ProblemDetail = {
  id: 1,
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
      { id: 1, title: '게시판 API 구현' },
      { id: 2, title: 'Todo 입력 예외 처리' },
      { id: 3, title: '사용자 프로필 컴포넌트' },
      { id: 4, title: '상품 목록 필터링' },
      { id: 5, title: '주문 상태 전이 검증' },
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

const mockRankingOwners: { ownerType: RankingOwnerType; ownerLabel: string }[] = [
  { ownerType: 'USER', ownerLabel: '프롬프트왕' },
  { ownerType: 'GUEST', ownerLabel: '8f2a' },
  { ownerType: 'USER', ownerLabel: 'tokenshaver' },
  { ownerType: 'USER', ownerLabel: '한줄이면충분' },
  { ownerType: 'GUEST', ownerLabel: 'c41d' },
  { ownerType: 'USER', ownerLabel: 'minimal_prompt' },
  { ownerType: 'GUEST', ownerLabel: '0b73' },
  { ownerType: 'USER', ownerLabel: '캐시장인' },
];

/**
 * 랭킹 한 줄을 만든다. 비용·토큰은 등수로 계산해 동점 줄이 같은 값을 갖게 한다 —
 * 등수와 비용이 어긋나면 표가 목에서만 이상해 보인다.
 */
function buildMockRankingEntry(
  rank: number,
  index: number,
  mineIndex: number | null,
): RankingEntry {
  const owner = mockRankingOwners[index % mockRankingOwners.length];
  const mine = index === mineIndex;

  return {
    rank,
    // attemptId는 내 줄에만 온다 — 남의 줄에 링크가 생기면 목이 실서버와 어긋난다.
    attemptId: mine ? 4200 + rank : null,
    mine,
    ownerType: mine ? 'USER' : owner.ownerType,
    ownerLabel: mine ? '내닉네임' : owner.ownerLabel,
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

  return {
    turns: turns.map((turn, index) => ({
      turn: index + 1,
      feedbackMd: `## 관찰\n\n${index + 1}번째 프롬프트는 "${turn.prompt.slice(0, 30)}…" 형태로 요청했습니다.\n\n## 개선 제안\n\nHTTP 메서드와 경로, 요청·응답 형식을 함께 명시하면 의도가 더 정확히 전달됩니다.`,
    })),
    overallMd: `## 세션 총평\n\n총 ${turns.length}개의 턴으로 문제를 풀었습니다.\n\n초반 프롬프트에서 도메인 모델과 API 계층을 한 번에 요구하기보다, 단계를 나눠 요청하면 AI가 의도를 덜 추측합니다.`,
  };
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}
