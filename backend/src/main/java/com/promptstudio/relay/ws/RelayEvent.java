package com.promptstudio.relay.ws;

/**
 * WebSocket으로 내보내는 이벤트 봉투.
 *
 * <p>맨 payload가 아니라 {@code type}을 붙여 보낸다 — 한 소켓으로 게임 상태와 WebRTC 시그널링을
 * 함께 나르므로 수신 측이 무엇인지 알아야 하고, 새 이벤트를 추가할 때 클라이언트를 깨뜨리지 않는다.
 */
public record RelayEvent(String type, Object payload) {

    /** 방의 전체 스냅샷. 접속 직후와 상태 변경마다 보낸다. */
    public static final String ROOM_STATE = "room.state";

    /** 턴 하나가 완료됐다. 요약과 변경 파일 경로가 실리고, 곧이어 room.state가 따라온다. */
    public static final String TURN_FINISHED = "turn.finished";

    /** 턴의 코드 생성이 실패해 같은 좌석이 재시도한다. 곧이어 room.state가 따라온다. */
    public static final String TURN_FAILED = "turn.failed";

    /** 턴이 치지 않은 채 건너뛰어졌다(이탈·입력 마감 초과). 곧이어 room.state가 따라온다. */
    public static final String TURN_SKIPPED = "turn.skipped";

    public static RelayEvent roomState(Object room) {
        return new RelayEvent(ROOM_STATE, room);
    }

    /** 이번 턴의 자동 채점이 큐에 들어갔다. */
    public static final String GRADING_STARTED = "grading.started";

    /** 채점이 끝나 좌석이 전진했다. 통과 수·증가분·실패 케이스가 실리고, room.state가 따라온다. */
    public static final String GRADING_FINISHED = "grading.finished";

    /** 피드백까지 준비 완료. 전원이 피드백 페이지로 이동한다. */
    public static final String FEEDBACK_READY = "feedback.ready";

    /** 피드백 생성 실패. 재시도 API를 기다린다. */
    public static final String FEEDBACK_FAILED = "feedback.failed";

    /** 접속 직후 자신에게만 오는, 이미 접속 중인 다른 피어들. 새로 온 쪽이 이들에게 offer를 만든다. */
    public static final String PEER_LIST = "peer.list";

    /** 피어가 시그널링 채널에 붙었다. 기존 피어들은 이 피어의 offer를 기다린다. */
    public static final String PEER_JOINED = "peer.joined";

    /** 피어의 소켓이 끊겼다. 그 피어와의 RTCPeerConnection을 정리한다. */
    public static final String PEER_LEFT = "peer.left";

    /**
     * WebRTC 시그널링 중계. 서버는 payload를 해석하지 않고 발신자만 확정해 전달한다 —
     * offer/answer/ice 구분은 세 가지 타입 문자열 그대로 오간다.
     */
    public static final String SIGNAL_OFFER = "signal.offer";
    public static final String SIGNAL_ANSWER = "signal.answer";
    public static final String SIGNAL_ICE = "signal.ice";

    /** 시그널 전달 실패(대상이 접속 중이 아님 등). 보낸 사람에게만 돌아간다. */
    public static final String SIGNAL_ERROR = "signal.error";

    public static RelayEvent turnFinished(Object summary) {
        return new RelayEvent(TURN_FINISHED, summary);
    }

    public static RelayEvent turnFailed(Object payload) {
        return new RelayEvent(TURN_FAILED, payload);
    }

    public static RelayEvent turnSkipped(Object payload) {
        return new RelayEvent(TURN_SKIPPED, payload);
    }

    public static RelayEvent gradingStarted(Object payload) {
        return new RelayEvent(GRADING_STARTED, payload);
    }

    public static RelayEvent gradingFinished(Object payload) {
        return new RelayEvent(GRADING_FINISHED, payload);
    }

    public static RelayEvent feedbackReady(Object room) {
        return new RelayEvent(FEEDBACK_READY, room);
    }

    public static RelayEvent feedbackFailed(Object room) {
        return new RelayEvent(FEEDBACK_FAILED, room);
    }
}
