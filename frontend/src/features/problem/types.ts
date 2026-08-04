export interface ProblemSummary {
  id: number;
  title: string;
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
