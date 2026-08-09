package com.promptstudio.attempt.domain;

/**
 * 피드백 화면이 한 번에 받는 것. 제출된 어템프트와, 그 세션에서 계산한 규칙 한 줄이다.
 *
 * <p>둘을 한 봉투에 담는 이유는 <b>출처가 다르기 때문</b>이다. {@link AttemptView#feedback()}과
 * {@link AttemptView#patternFeedback()}은 제출 때 LLM이 써서 저장한 값이고, {@link CarryLine}은
 * 저장하지 않고 읽을 때마다 계산한다. {@code AttemptView}에 끼워 넣으면 어템프트를 만드는 모든
 * 자리가 이 값을 아는 척해야 하는데, 조회 seam도 엔티티 경로도 그것을 계산할 재료가 없다.
 *
 * <p>계산이라서 얻는 것이 하나 더 있다 — 규칙이 생기기 전에 제출된 어템프트도 열면 바로 받는다.
 * 마이그레이션이 없다.
 *
 * @param carry 이 세션이 어느 신호에도 안 걸리면 null이다. 그때는 화면에 아무것도 올리지 않는다
 */
public record FeedbackView(AttemptView attempt, CarryLine carry) {
}
