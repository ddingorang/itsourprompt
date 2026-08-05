package com.promptstudio.relay.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 필드 이름이 RTCPeerConnection 설정({@code iceServers: [{urls, username, credential}]})과
 * 일치한다 — 클라이언트가 응답을 가공 없이 생성자에 넣을 수 있어야 한다.
 */
@Schema(description = "WebRTC ICE 서버 설정. 응답을 그대로 RTCPeerConnection 생성자에 넣는다")
public record IceServersResponse(
        @Schema(description = "ICE 서버 목록. TURN이 설정되지 않은 환경(로컬 개발)에서는 STUN만 온다")
        List<IceServerResponse> iceServers
) {

    @Schema(description = "ICE 서버 하나")
    public record IceServerResponse(
            @Schema(description = "서버 URL들", example = "[\"stun:stun.l.google.com:19302\"]")
            List<String> urls,
            @Schema(description = "TURN 사용자명. STUN이면 null", example = "relay")
            String username,
            @Schema(description = "TURN 비밀번호. STUN이면 null")
            String credential
    ) {
    }
}
