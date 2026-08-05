/**
 * 탭 줄(`role="tablist"`)의 방향키 이동.
 *
 * 탭 규약상 선택되지 않은 탭은 `tabIndex={-1}`이라 Tab 키로 닿지 않는다. 방향키를 처리하지 않으면
 * 키보드만 쓰는 사람은 지금 선택된 탭에서 다른 탭으로 갈 길이 없다.
 *
 * <p>다음 자리만 계산하고 선택과 포커스는 부르는 쪽이 자기 state로 한다 — 탭마다 무엇을 선택하고
 * 어디에 포커스를 줄지가 다르고, DOM에서 선택 상태를 되읽으면 state가 진실의 근원이 아니게 된다.
 */
export function nextTabIndex(
  key: string,
  currentIndex: number,
  tabCount: number,
): number | null {
  if (key === 'ArrowRight') return (currentIndex + 1) % tabCount;
  if (key === 'ArrowLeft') return (currentIndex - 1 + tabCount) % tabCount;
  if (key === 'Home') return 0;
  if (key === 'End') return tabCount - 1;

  return null;
}
