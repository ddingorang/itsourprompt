// prism-react-renderer normalizeTokens와 같은 정규식이다. 개행마다 새 줄을 만들고
// 끝 개행이 남기는 빈 줄도 버리지 않으므로, split 결과 길이가 Highlight tokens 길이와
// 항상 같다. 이게 어긋나면 강조가 통째로 한 줄씩 밀린다 — trim 금지.
const NEWLINE_RE = /\r\n|\r|\n/;

export type DiffLineStatus = 'unchanged' | 'added' | 'changed';

export interface LineDiff {
  /** after 각 줄의 상태. 인덱스는 Highlight tokens의 줄 인덱스와 1:1. */
  statuses: DiffLineStatus[];
  /**
   * 그 자리에서 사라진 줄 묶음(접힌 마커가 펼칠 내용). 변경으로 짝지어진
   * 삭제 줄도 포함한다 — 바뀌기 전 텍스트를 펼쳐 볼 유일한 자리다.
   * 키는 마커를 앞에 둘 after 줄 인덱스. afterLines.length면 파일 끝.
   */
  deletions: Map<number, string[]>;
}

/**
 * 직전 상태 대비 줄 단위 diff. before가 null이면 새로 생긴 파일로 본다.
 * 줄번호를 밀지 않으려고 결과는 언제나 after 기준이다 — 삭제된 줄은 statuses에
 * 들어가지 않고 deletions의 접힌 묶음으로만 남는다.
 */
export function diffLines(before: string | null, after: string): LineDiff {
  const afterLines = after.split(NEWLINE_RE);

  if (before === null) {
    return { statuses: afterLines.map(() => 'added'), deletions: new Map() };
  }
  if (before === after) {
    return { statuses: afterLines.map(() => 'unchanged'), deletions: new Map() };
  }

  const beforeLines = before.split(NEWLINE_RE);
  const m = beforeLines.length;
  const n = afterLines.length;

  // dp[i][j] = beforeLines[i..] vs afterLines[j..]의 LCS 길이. 파일이 수십 줄이라
  // O(n·m)으로 충분하다.
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array<number>(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i -= 1) {
    for (let j = n - 1; j >= 0; j -= 1) {
      dp[i][j] =
        beforeLines[i] === afterLines[j]
          ? dp[i + 1][j + 1] + 1
          : Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }

  const statuses: DiffLineStatus[] = afterLines.map(() => 'unchanged');
  const deletions = new Map<number, string[]>();

  // 연속된 삭제·추가를 hunk 하나로 모았다가 match를 만나면 확정한다.
  let pendingDeleted: string[] = [];
  let addStart = -1;
  let addCount = 0;

  const flush = (currentJ: number) => {
    // 짝짓기는 추가 줄을 '~'로 그릴지 '+'로 그릴지만 정한다. 마커에 담을 삭제 줄과는
    // 무관한 별개 관심사다.
    const paired = Math.min(pendingDeleted.length, addCount);
    for (let k = 0; k < addCount; k += 1) {
      statuses[addStart + k] = k < paired ? 'changed' : 'added';
    }
    if (pendingDeleted.length > 0) {
      // 마커는 hunk 앞 — 삭제된 줄이 원래 있던 자리다. 순수 삭제면 사라진 자리(currentJ),
      // 파일 끝이면 n이 된다.
      deletions.set(addCount > 0 ? addStart : currentJ, pendingDeleted);
    }
    pendingDeleted = [];
    addStart = -1;
    addCount = 0;
  };

  let i = 0;
  let j = 0;
  while (i < m || j < n) {
    if (i < m && j < n && beforeLines[i] === afterLines[j]) {
      flush(j);
      i += 1;
      j += 1;
    } else if (i < m && (j >= n || dp[i + 1][j] >= dp[i][j + 1])) {
      pendingDeleted.push(beforeLines[i]);
      i += 1;
    } else {
      if (addCount === 0) addStart = j;
      addCount += 1;
      j += 1;
    }
  }
  flush(j);

  return { statuses, deletions };
}
