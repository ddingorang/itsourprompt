import type { StoredFeedbackResult } from '../feedback/types';
import type { StoredRunResult } from './types';

const RUN_RESULT_KEY = 'promptPracticeRunResult';
const FEEDBACK_RESULT_KEY = 'promptPracticeFeedbackResult';

export function saveRunResult(result: StoredRunResult): void {
  sessionStorage.setItem(RUN_RESULT_KEY, JSON.stringify(result));
}

export function getRunResult(): StoredRunResult | null {
  try {
    const saved = sessionStorage.getItem(RUN_RESULT_KEY);
    return saved ? (JSON.parse(saved) as StoredRunResult) : null;
  } catch {
    return null;
  }
}

export function saveFeedbackResult(result: StoredFeedbackResult): void {
  sessionStorage.setItem(FEEDBACK_RESULT_KEY, JSON.stringify(result));
}

export function getFeedbackResult(): StoredFeedbackResult | null {
  try {
    const saved = sessionStorage.getItem(FEEDBACK_RESULT_KEY);
    return saved ? (JSON.parse(saved) as StoredFeedbackResult) : null;
  } catch {
    return null;
  }
}
