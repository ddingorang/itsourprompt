package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.CodeRunResult;

/**
 * 워커의 실행 결과가 code_run에 반영되었다. 결과를 기다리는 다른 도메인(릴레이 게임의 매 턴 채점)이
 * 구독한다 — 어템프트 도메인은 구독자를 모른다.
 *
 * <p>결과가 실제로 반영된 경우에만 발행된다. 메시지 재전달이나 TTL 회수와 겹쳐 무시된 결과까지
 * 발행하면 구독자가 같은 턴을 두 번 전진시킨다.
 *
 * <p>결과 전체를 싣는다. 구독자가 runId로 다시 조회하게 하면 조회 시점의 상태가 반영 시점과
 * 다를 수 있고, 케이스 집계에 필요한 것이 모두 {@link CodeRunResult}에 이미 있다.
 */
public record CodeRunFinishedEvent(CodeRunResult result) {
}
