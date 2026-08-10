/**
 * 진행 중임을 알리는 회전 원. 결과를 기다리는 동안 화면이 멈춘 것처럼 보이면
 * 사용자가 오류로 오해하므로, 움직이는 표시를 함께 둔다.
 *
 * 색은 currentColor를 따르고 한 곳만 비워 회전이 보이게 한다 — 감싼 요소의
 * 글자색을 그대로 물려받아 문구와 같은 톤으로 돈다.
 */
export default function Spinner({
  className = '',
  size = 16,
}: {
  className?: string;
  size?: number;
}) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block shrink-0 animate-spin rounded-full border-2 border-current border-t-transparent ${className}`}
      style={{ height: size, width: size }}
    />
  );
}
