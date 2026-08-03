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
  Turn,
} from '../features/attempt/types';
import type {
  ProblemDetail,
  ProblemListResponse,
} from '../features/problem/types';
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

export async function getMockProblems(): Promise<ProblemListResponse> {
  await delay(250);
  return {
    problems: [
      { id: 1, title: '게시판 API 구현' },
      { id: 2, title: 'Todo 입력 예외 처리' },
      { id: 3, title: '사용자 프로필 컴포넌트' },
      { id: 4, title: '상품 목록 필터링' },
    ],
  };
}

export async function getMockProblemDetail(problemId: number): Promise<ProblemDetail> {
  await delay(250);
  return { ...problemDetail, id: problemId };
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
