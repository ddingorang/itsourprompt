import type { RepositoryFile } from '../problem/types';
import type { Turn } from './types';

/**
 * 백엔드 FileReplay.head와 같은 규칙으로 base에 턴 변경을 순서대로 재생한다.
 * 경로는 백엔드처럼 raw 그대로 키로 쓴다(정규화는 조회 쪽 findFile 담당).
 * 기존 경로는 자리를 지키고 새 경로만 뒤에 붙는다 — JS Map 삽입 순서가
 * 백엔드 LinkedHashMap과 같다.
 */
export function replayFiles(baseFiles: RepositoryFile[], turns: Turn[]): RepositoryFile[] {
  const contents = new Map<string, string>();
  for (const file of baseFiles) contents.set(file.path, file.content);
  for (const turn of turns)
    for (const change of turn.changedFiles) {
      if (change.changeType === 'DELETED') contents.delete(change.path);
      else contents.set(change.path, change.content ?? '');
    }
  return [...contents].map(([path, content]) => ({ path, content }));
}
