package com.promptstudio.relay.domain;

/**
 * 릴레이 방의 진행 단계. 상태 머신 전체는 docs/relay-game-plan.md 참고.
 *
 * <p>모든 턴이 {@code TURN_GENERATING → TURN_GRADING}을 똑같이 거치고, 마지막 턴이었을 때만
 * 그 뒤에 {@code FEEDBACK_GENERATING}이 한 단계 붙는다 — 마지막 턴은 특별 케이스가 아니다.
 */
public enum RelayRoomStatus {

    /** 입장을 받는 중. 방장이 시작하면 좌석이 확정된다. */
    WAITING,

    /** 현재 좌석의 주자가 프롬프트를 입력할 차례. */
    PLAYING,

    /** 프롬프트를 받아 AI가 코드를 생성하는 중. */
    TURN_GENERATING,

    /** 생성된 코드의 빌드/테스트 채점 결과를 기다리는 중. */
    TURN_GRADING,

    /** 마지막 턴까지 끝나 피드백을 생성하는 중. */
    FEEDBACK_GENERATING,

    /** 피드백까지 준비 완료. 전원이 피드백을 확인할 수 있다. */
    FINISHED
}
