package com.promptstudio.relay.exception;

import com.promptstudio.relay.domain.RelayRoomStatus;

/**
 * 주자를 기다리는 상태(PLAYING)가 아니어서 턴을 받을 수 없다. 다른 턴이 생성 중이거나,
 * 시작 전이거나, 이미 끝난 방이다.
 */
public class RelayGameNotPlayingException extends RuntimeException {

    public RelayGameNotPlayingException(Long roomId, RelayRoomStatus status) {
        super("릴레이 방 ID " + roomId + "는 지금 턴을 받을 수 없습니다. (상태: " + status + ")");
    }
}
