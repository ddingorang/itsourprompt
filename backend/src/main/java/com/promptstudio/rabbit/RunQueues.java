package com.promptstudio.rabbit;

/**
 * 백엔드와 buildandtest 워커가 공유하는 토폴로지 이름. 워커 쪽에 같은 값이 중복 정의되어 있다.
 */
public final class RunQueues {

    public static final String EXCHANGE = "run.exchange";
    public static final String REQUEST_ROUTING_KEY = "run.request";
    public static final String REQUEST_QUEUE = "run.request";
    public static final String RESULT_ROUTING_KEY = "run.result";
    public static final String RESULT_QUEUE = "run.result";

    static final String DEAD_LETTER_EXCHANGE = "run.dlx";
    static final String REQUEST_DLQ_ROUTING_KEY = "run.request.dlq";
    static final String REQUEST_DLQ = "run.request.dlq";

    private RunQueues() {
    }
}
