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
  RepositoryFile,
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

/** 목에서 나를 부르는 이름. 랭킹의 내 줄과 어템프트 주인이 같은 이름이어야 한다. */
const MY_MOCK_OWNER_LABEL = '내닉네임';

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
 * 랭킹 줄이 가리키는 목 어템프트 ID를 `기준 + 문제ID*칸수 + 자리`로 짠다.
 *
 * ID 하나에 문제까지 실어야 하는 이유는, 어템프트 조회가 받는 것이 ID뿐이기 때문이다.
 * 문제를 못 실으면 지어낸 어템프트가 어느 랭킹에서 왔든 같은 문제를 말하게 되고,
 * 그러면 문제 3의 랭킹에서 연 피드백 화면이 "이 문제 풀어보기"로 문제 1을 가리킨다.
 * 목이 실서버와 어긋나는 자리가 되므로 ID로 되짚을 수 있게 둔다.
 */
const RANKED_ATTEMPT_ID_BASE = 4000;
const RANKED_SLOTS_PER_PROBLEM = 100;

/** 문제마다 0번 자리는 내 줄이다. 그래야 조회가 ID만 보고 주인을 되돌려 준다. */
const MY_RANKED_SLOT = 0;

/**
 * 실행 기록이 하나도 없는 남의 제출 자리(어느 문제에서든 99번). 랭킹에 오르려면 마지막
 * 턴이 채점을 통과해야 해서 보통은 기록이 있지만, 랭킹 밖의 제출은 한 번도 돌려 보지
 * 않고 낼 수 있다. 그 화면을 열어 볼 자리가 없으면 기록 없는 경우를 목으로 확인할 수 없다.
 */
const RANKED_SLOT_WITHOUT_RUNS = 99;

function rankedAttemptId(problemId: number, slot: number): number {
  return RANKED_ATTEMPT_ID_BASE + problemId * RANKED_SLOTS_PER_PROBLEM + slot;
}

/** 지어낸 랭킹 어템프트가 아니면 null. 그때 조회는 404가 된다. */
function decodeRankedAttemptId(
  attemptId: number,
): { problemId: number; slot: number } | null {
  const offset = attemptId - RANKED_ATTEMPT_ID_BASE;
  // 문제 ID는 1부터다. 한 칸 아래(offset < 칸수)는 문제 0이라 지어낼 수 없다.
  if (offset < RANKED_SLOTS_PER_PROBLEM) return null;

  const problemId = Math.floor(offset / RANKED_SLOTS_PER_PROBLEM);
  // getMockProblemDetail이 404를 내는 대역이면 어템프트도 있을 수 없다.
  if (problemId >= 100) return null;

  return { problemId, slot: offset % RANKED_SLOTS_PER_PROBLEM };
}

/**
 * 랭킹 한 줄을 만든다. 비용·토큰은 등수로 계산해 동점 줄이 같은 값을 갖게 한다 —
 * 등수와 비용이 어긋나면 표가 목에서만 이상해 보인다.
 */
function buildMockRankingEntry(
  problemId: number,
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
    attemptId: rankedAttemptId(problemId, mine ? MY_RANKED_SLOT : index + 1),
    mine,
    ownerLabel: mine ? MY_MOCK_OWNER_LABEL : nickname,
    cost: 0.0012 + (rank - 1) * 0.00037,
    uncachedInputTokens: 1500 + (rank - 1) * 220,
    cachedInputTokens: 400 + (rank - 1) * 130,
    outputTokens: 500 + (rank - 1) * 90,
    turns: 1 + (rank % 4),
    rounds: 2 + (rank % 5),
    submittedAt: `2026-08-0${(rank % 3) + 1}T0${rank % 9}:1${rank % 9}:32Z`,
    // 표기 네 구간(초·분·시간·일)을 목에서 전부 보여준다. rank 9는 null — 제출 시각을
    // 모르는 옛 기록의 '--'를 그린다.
    durationSeconds: rank === 9 ? null : [190800, 42, 252, 4980][rank % 4],
  };
}

function buildMockRanking(
  problemId: number,
  ranks: number[],
  mineIndex: number | null,
  totalCount = ranks.length,
): ProblemRanking {
  const entries = ranks.map((rank, index) =>
    buildMockRankingEntry(problemId, rank, index, mineIndex),
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
    // 생성 응답만 ownerLabel이 null이다 — 실서버가 이 경로에서만 닉네임을 조인하지
    // 않는다. 목이 채워 주면 생성 직후 빈칸이 나는 화면을 목에서 못 잡는다.
    ownerLabel: null,
    mine: true,
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

/**
 * 랭킹이 가리키는 어템프트를 ID만 보고 지어낸다. 남이 제출한 어템프트를 읽는 상태는
 * 손으로 만들 수 없어, 목에서 그 화면을 여는 유일한 길이다. 주인은 ID 대역으로 가른다.
 */
function buildRankedMockAttempt(attemptId: number): Attempt | null {
  const decoded = decodeRankedAttemptId(attemptId);
  if (!decoded) return null;

  const { problemId, slot } = decoded;
  const mine = slot === MY_RANKED_SLOT;

  const builtTurns = [
    '게시글 엔티티와 CRUD API 계층을 만들어 주세요.',
    '앞 턴에서 만든 레코드에 작성 시각을 넣고 목록을 최신순으로 정렬해 주세요.',
  ].map((prompt, index) => buildMockTurn(prompt, index + 1));

  return {
    id: attemptId,
    // 이 어템프트가 실제로 푼 문제다. 랭킹 줄에서 왔으면 그 랭킹의 문제이므로,
    // 피드백 화면의 "이 문제 풀어보기"가 엉뚱한 문제로 가지 않는다.
    problemId,
    baseFiles: problemDetail.files,
    files: [...problemDetail.files, ...builtTurns.map((built) => built.addedFile)],
    turns: builtTurns.map((built) => built.turn),
    status: 'SUBMITTED',
    ownerLabel: mine
      ? MY_MOCK_OWNER_LABEL
      : mockRankingNicknames[(slot - 1) % mockRankingNicknames.length],
    mine,
  };
}

/**
 * 지어낸 어템프트에 통과한 실행 기록을 함께 심는다. 남의 제출을 여는 화면은 실행을
 * 요청할 수 없고 기록만 읽으므로, 기록이 없으면 그 화면이 목에서 늘 비어 보인다.
 *
 * 두 케이스를 모두 PASSED로 둔다 — 랭킹에 오르려면 마지막 턴이 채점을 통과해야 하고,
 * 링크를 받아 들어온 사람이 확인하러 오는 것도 그 사실이다.
 */
function seedRankedMockCodeRuns(attemptId: number): void {
  if (decodeRankedAttemptId(attemptId)?.slot === RANKED_SLOT_WITHOUT_RUNS) return;
  if (mockCodeRuns.has(attemptId)) return;

  mockCodeRuns.set(attemptId, [
    {
      createdAt: '2026-08-05T09:12:34Z',
      readyAt: Date.parse('2026-08-05T09:12:35Z'),
      run: {
        runId: `recorded-${attemptId}`,
        // 마지막 턴(0-based)의 실행이다. 지어낸 어템프트의 턴은 둘이다.
        turnOrdinal: 1,
        status: 'SUCCEEDED',
        exitCode: 0,
        stdout: 'JUnit Platform Suite\nPostTest: 2 passed, 0 failed',
        stderr: null,
        durationMs: 1188,
        cases: [
          {
            className: 'PostTest',
            name: '게시글을_생성한다()',
            status: 'PASSED',
            message: null,
            durationMs: 11,
          },
          {
            className: 'PostTest',
            name: '존재하지_않는_게시글은_예외를_반환한다()',
            status: 'PASSED',
            message: null,
            durationMs: 14,
          },
        ],
      },
    },
  ]);
}

/**
 * 메모리에 있으면 그것을, 없으면 랭킹이 가리키는 어템프트를 지어내 담아 둔다 —
 * 조회와 피드백이 같은 어템프트를 집어야 두 응답이 어긋나지 않는다.
 */
function resolveMockAttempt(attemptId: number): Attempt | undefined {
  const stored = mockAttempts.get(attemptId);
  if (stored) return stored;

  const ranked = buildRankedMockAttempt(attemptId);
  if (!ranked) return undefined;

  mockAttempts.set(attemptId, ranked);
  seedRankedMockCodeRuns(attemptId);
  return ranked;
}

export async function getMockAttempt(attemptId: number): Promise<Attempt> {
  await delay(200);

  const attempt = resolveMockAttempt(attemptId);
  if (!attempt) {
    throw mockAttemptNotFound(attemptId);
  }
  // 조회는 생성과 달리 주인 이름을 채워 준다.
  return { ...attempt, ownerLabel: attempt.ownerLabel ?? MY_MOCK_OWNER_LABEL };
}

/** 한 턴의 사용량. 총계를 더하는 쪽이 null을 만나지 않도록 항목을 모두 채운다. */
function buildMockTurnUsage(turnNumber: number) {
  return {
    inputTokens: 2500 + (turnNumber - 1) * 320,
    uncachedInputTokens: 1500 + (turnNumber - 1) * 200,
    cachedInputTokens: 1000 + (turnNumber - 1) * 120,
    outputTokens: 500 + (turnNumber - 1) * 80,
    reasoningTokens: 120 + (turnNumber - 1) * 20,
    latencyMs: 260 + (turnNumber - 1) * 35,
    cost: 0.003 + (turnNumber - 1) * 0.0004,
  };
}

/** 턴 하나와 그 턴이 새로 만든 파일. 턴을 쌓는 자리와 지어내는 자리가 같이 쓴다. */
function buildMockTurn(
  prompt: string,
  turnNumber: number,
): {
  addedFile: RepositoryFile;
  turn: Turn;
  usage: ReturnType<typeof buildMockTurnUsage>;
} {
  const turnUsage = buildMockTurnUsage(turnNumber);
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

  return { addedFile, turn, usage: turnUsage };
}

export async function addMockTurn(attemptId: number, prompt: string): Promise<Attempt> {
  await delay(800);

  const attempt = mockAttempts.get(attemptId);
  if (!attempt) {
    throw mockAttemptNotFound(attemptId);
  }
  // 턴 추가도 쓰기다 — 실행 요청과 같은 이유로 주인만 할 수 있다.
  if (!attempt.mine) throw mockAttemptNotFound(attemptId);

  const { addedFile, turn, usage: turnUsage } = buildMockTurn(
    prompt,
    attempt.turns.length + 1,
  );

  const updated: Attempt = {
    ...attempt,
    // 턴 추가는 커밋 뒤 다시 읽으므로 생성과 달리 주인 이름이 채워져 온다.
    ownerLabel: attempt.ownerLabel ?? MY_MOCK_OWNER_LABEL,
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
  // 쓰기는 제출 여부와 무관하게 주인만 할 수 있다. 남이 부르면 실서버와 같이
  // 없는 어템프트로 답한다 — 목이 실행시켜 주면 화면이 남의 풀이를 돌릴 수 있는
  // 것처럼 보이고, 그 착각은 실서버에서만 404로 드러난다.
  if (!attempt.mine) throw mockAttemptNotFound(attemptId);

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

/** 턴 기록으로 피드백 본문을 짓는다. 제출과 조회가 같은 내용을 돌려주게 하는 자리다. */
function buildMockFeedback(turns: Turn[]): AttemptFeedback {
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
 * 제출은 쓰기다. 제출 여부와 무관하게 주인만 할 수 있고, 남이 부르면 실서버와 같이
 * 없는 어템프트로 답한다 — 목이 받아 주면 남의 풀이를 내 이름으로 낼 수 있는 것처럼
 * 보이고, 그 착각은 실서버에서만 404로 드러난다.
 *
 * 피드백 조회와 한 함수였던 것을 가른 이유가 이것이다. 실서버에서 그 둘은 쓰기와
 * 공개 읽기로 갈리므로, 목이 하나로 묶으면 어느 쪽 규칙도 흉내 낼 수 없다.
 */
export async function submitMockAttempt(attemptId: number): Promise<AttemptFeedback> {
  await delay(600);

  const attempt = resolveMockAttempt(attemptId);
  if (attempt && !attempt.mine) throw mockAttemptNotFound(attemptId);

  const turns = attempt?.turns ?? [];

  mockAttempts.set(attemptId, {
    ...(attempt ?? {
      id: attemptId,
      problemId: problemDetail.id,
      baseFiles: problemDetail.files,
      files: problemDetail.files,
      turns: [],
      status: 'IN_PROGRESS',
      ownerLabel: MY_MOCK_OWNER_LABEL,
      mine: true,
    }),
    status: 'SUBMITTED',
  });

  return buildMockFeedback(turns);
}

/**
 * 피드백 조회는 공개 읽기다. 제출된 어템프트면 주인이 아니어도 200이고, 제출 전이면
 * 실서버와 같이 feedback-not-found다 — 화면이 그 code로 "아직 제출하지 않았습니다"
 * 안내를 고르므로 attempt-not-found로 뭉뚱그리면 목에서만 다른 화면이 나온다.
 */
export async function getMockAttemptFeedback(
  attemptId: number,
): Promise<AttemptFeedback> {
  await delay(600);

  const attempt = resolveMockAttempt(attemptId);
  if (!attempt) throw mockAttemptNotFound(attemptId);

  if (attempt.status !== 'SUBMITTED') {
    throw new ApiError(404, {
      code: API_ERROR_CODES.feedbackNotFound,
      message: `목 어템프트 ${attemptId}는 아직 제출되지 않았습니다.`,
    });
  }

  return buildMockFeedback(attempt.turns);
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
