package com.promptstudio.rabbit;

/**
 * 백엔드와 buildandtest 워커가 공유하는 토폴로지 이름. 워커 쪽에 같은 값이 중복 정의되어 있다.
 *
 * <p>요청 큐는 언어별로 나뉜다 — 워커 하나는 언어 하나만 맡으므로(워커의 worker.language 참고)
 * 라우팅 자체를 큐 수준에서 가른다. 공유 큐에서 워커가 열어보고 되돌리는 방식은 재큐잉 루프가 된다.
 * 결과 큐는 나누지 않는다 — 백엔드는 결과를 언어 무관하게 처리한다.
 */
public final class RunQueues {

    public static final String EXCHANGE = "run.exchange";

    /**
     * 언어 도입 전의 요청 큐. 이행기 동안만 남는다 — 구버전 백엔드가 발행한 메시지를 java 워커가
     * 마저 비운다. 관리 UI에서 빈 것을 확인하면 선언과 워커 구독(WORKER_REQUEST_QUEUES)을 제거한다.
     */
    public static final String LEGACY_REQUEST_QUEUE = "run.request";

    /** direct exchange라 라우팅 키 = 큐 이름 규약을 쓴다. */
    public static final String REQUEST_QUEUE_JAVA = "run.request.java";
    public static final String REQUEST_QUEUE_PYTHON = "run.request.python";

    public static final String RESULT_ROUTING_KEY = "run.result";
    public static final String RESULT_QUEUE = "run.result";

    static final String DEAD_LETTER_EXCHANGE = "run.dlx";
    static final String LEGACY_REQUEST_DLQ = "run.request.dlq";
    static final String REQUEST_DLQ_JAVA = "run.request.java.dlq";
    static final String REQUEST_DLQ_PYTHON = "run.request.python.dlq";

    /**
     * 요청이 갈 라우팅 키. null은 언어 필드 도입 전의 값이라 java로 해석한다(계약 규칙).
     * 모르는 언어는 조용히 java 워커로 흘려보내지 않고 발행 시점에 터뜨린다 — 파서가 허용 언어를
     * 검증하므로 여기 닿는 것은 라우팅 추가를 빠뜨린 채 언어를 늘린 프로그래밍 오류다.
     */
    public static String requestRoutingKey(String language) {
        if (language == null || language.equals("java")) {
            return REQUEST_QUEUE_JAVA;
        }
        if (language.equals("python")) {
            return REQUEST_QUEUE_PYTHON;
        }

        throw new IllegalArgumentException("라우팅이 정의되지 않은 언어입니다: " + language);
    }

    private RunQueues() {
    }
}
