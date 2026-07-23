import type {
  ProblemDetail,
  ProblemListResponse,
} from '../features/problem/types';
import type {
  RunProblemRequest,
  RunProblemResponse,
} from '../features/submission/types';
import type {
  FeedbackResponse,
  SubmitFeedbackRequest,
} from '../features/feedback/types';

export const useMocks = import.meta.env.VITE_USE_MOCKS === 'true';

const problemDetail: ProblemDetail = {
  id: 1,
  title: '게시판 API 구현',
  specMd:
    '# 문제\n\n게시글 엔티티와 CRUD API 계층을 구현하세요. 각 실행은 원본 스켈레톤에서 새로 시작하며 코드는 직접 편집할 수 없습니다.',
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

export async function runMockProblem(
  _problemId: number,
  _request: RunProblemRequest,
): Promise<RunProblemResponse> {
  await delay(800);

  return {
    files: [
      ...problemDetail.files,
      {
        path: 'src/main/java/Post.java',
        content: `package com.example.board;

public record Post(Long id, String title, String content) {}`,
      },
      {
        path: 'src/main/java/PostService.java',
        content: `package com.example.board;

public class PostService {
  public Post create(String title, String content) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title is required");
    }
    return new Post(null, title, content);
  }
}`,
      },
    ],
    changedFiles: [
      { path: 'src/main/java/Post.java', changeType: 'ADDED' },
      { path: 'src/main/java/PostService.java', changeType: 'ADDED' },
      { path: 'src/main/java/App.java', changeType: 'MODIFIED' },
    ],
    aiResponse:
      'Post 엔티티와 서비스 계층을 추가하고 App.java의 시작 구성을 수정했습니다.',
  };
}

export async function submitMockFeedback(
  _problemId: number,
  request: SubmitFeedbackRequest,
): Promise<FeedbackResponse> {
  await delay(600);

  return {
    feedback: `## 프롬프트 관찰

의도는 게시판 도메인 모델과 CRUD 계층을 한 번에 구성하고 필수값 검증 및 계층 분리까지 포함하는 것입니다.

다음 요청에서는 각 API의 HTTP 메서드와 경로, 요청·응답 형식, 빈 값 처리 시 사용할 상태 코드를 구체적으로 명시해보세요. 현재 제출에는 ${request.changedFiles.length}개의 변경 파일이 포함되어 있습니다.`,
  };
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}
