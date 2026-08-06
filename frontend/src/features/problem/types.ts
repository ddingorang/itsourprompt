export interface ProblemSummary {
  id: number;
  title: string;
  /** 문제 타입. 리스트에서 game은 게임 아이콘 하나로, 그 외에는 풀이 언어 아이콘으로 표시한다. */
  type: 'coding' | 'game';
  /** 풀이 언어(java, python, …). */
  language: string;
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
