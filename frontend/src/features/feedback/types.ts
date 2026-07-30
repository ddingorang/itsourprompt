import type { ChangedFile } from '../submission/types';

export interface SubmitFeedbackRequest {
  prompt: string;
  aiResponse: string;
  changedFiles: ChangedFile[];
}

export interface FeedbackResponse {
  feedback: string;
}

export interface StoredFeedbackResult {
  problemId: number;
  problemTitle: string;
  prompt: string;
  feedback: string;
}
