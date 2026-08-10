export interface ProblemSummary {
  id: number;
  title: string;
  /**
   * 문제 타입. 리스트에서 game은 게임 아이콘 하나로, 그 외에는 풀이 언어 아이콘으로 표시한다.
   *
   * 두 필드를 싣지 않는 BE와 겹쳐 배포되면 응답에 없다 — 실제로 목록 페이지가 통째로 죽은
   * 전례가 있어 optional로 두고, 없을 때의 처리를 컴파일러가 강제하게 한다.
   */
  type?: 'coding' | 'game';
  /** 풀이 언어(java, python, …). type과 같은 배포에서 함께 실리므로 함께 없다. */
  language?: string;
}

export interface ProblemListResponse {
  problems: ProblemSummary[];
}

export interface RepositoryFile {
  path: string;
  content: string;
}

export interface ProblemDetail {
  id: number;
  title: string;
  specMd: string;
  type: 'coding' | 'game';
  files: RepositoryFile[];
}
