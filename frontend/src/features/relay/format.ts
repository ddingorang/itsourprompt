/**
 * 릴레이 화면들이 공유하는 표시 포맷. 로비 목록과 대기실이 같은 값을 다르게
 * 읽어주면 "설정한 값이 그대로 들어간 게 맞나" 싶어지므로 한 곳에 둔다.
 */

/** 턴 제한시간(초)을 "2분", "1분 30초", "30초"처럼 읽어준다. */
export function formatTurnTimeLimit(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;

  if (minutes === 0) return `${remainder}초`;
  if (remainder === 0) return `${minutes}분`;

  return `${minutes}분 ${remainder}초`;
}
