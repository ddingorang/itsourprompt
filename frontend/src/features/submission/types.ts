import type { RepositoryFile } from '../problem/types';

export type ChangeType = 'ADDED' | 'MODIFIED' | 'DELETED';

export interface ChangedFile {
  path: string;
  changeType: ChangeType;
}

export interface RunProblemRequest {
  prompt: string;
}

export interface RunProblemResponse {
  files: RepositoryFile[];
  changedFiles: ChangedFile[];
  aiResponse: string;
}

export interface StoredRunResult extends RunProblemResponse {
  problemId: number;
  problemTitle: string;
  prompt: string;
}
