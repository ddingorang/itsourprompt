package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayRoomView;

/**
 * 방 상태가 바뀌었다. WebSocket 어댑터가 받아 접속자 전원에게 스냅샷을 밀어준다.
 *
 * <p>서비스가 브로드캐스터를 직접 부르지 않고 이벤트를 쓰는 이유는 둘이다. 도메인 서비스가 전송
 * 수단을 몰라도 되고, 커밋 이후에 보낼 수 있다 — 트랜잭션 안에서 보내면 롤백된 상태를 접속자들이
 * 이미 화면에 그린 뒤가 된다.
 *
 * <p>스냅샷을 이벤트에 실어 보낸다. 수신 측이 다시 조회하면 그 사이 다음 변경이 끼어들어
 * 이벤트 순서와 내용이 어긋날 수 있다.
 */
public record RelayRoomChanged(RelayRoomView room) {
}
