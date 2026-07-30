package com.promptstudio.buildandtest;

/**
 * 백엔드와 공유하는 토폴로지 이름. 백엔드의 {@code com.promptstudio.rabbit.RunQueues}에 같은 값이 있다.
 *
 * <p>큐 선언은 백엔드가 단독으로 한다 — 양쪽이 선언하면 속성이 다를 때 PRECONDITION_FAILED가 난다.
 * 워커는 선언하지 않고 missing-queues-fatal=false로 큐가 생길 때까지 재시도한다.
 */
final class RunQueues {

    static final String EXCHANGE = "run.exchange";
    static final String REQUEST_QUEUE = "run.request";
    static final String RESULT_ROUTING_KEY = "run.result";

    private RunQueues() {
    }
}
