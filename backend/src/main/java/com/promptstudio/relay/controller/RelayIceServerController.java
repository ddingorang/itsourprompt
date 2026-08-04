package com.promptstudio.relay.controller;

import com.promptstudio.relay.config.RelayWebRtcProperties;
import com.promptstudio.relay.controller.response.IceServersResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * TURN 자격증명이 응답에 실리므로 로그인 사용자에게만 연다(/api/relay/** 인증 규칙).
 * 정적 계정이라 유출되면 중계 대역폭을 도둑맞는 정도의 위험이고, 규모가 커지면 시간제한
 * HMAC 자격증명으로 바꾼다(RelayWebRtcProperties 참고).
 */
@RestController
@RequestMapping("/api/relay/ice-servers")
@Tag(name = "Relay", description = "릴레이 게임 API — 여러 사람이 한 문제를 순서대로 이어 푼다")
public class RelayIceServerController {

    private final RelayWebRtcProperties properties;

    public RelayIceServerController(RelayWebRtcProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    @Operation(
            summary = "ICE 서버 설정 조회",
            description = "RTCPeerConnection 생성자에 그대로 넣을 수 있는 ICE 서버 목록을 반환합니다. "
                    + "TURN이 설정되지 않은 환경(로컬 개발)에서는 STUN만 옵니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = IceServersResponse.class))
            )
    })
    public IceServersResponse getIceServers() {
        List<IceServersResponse.IceServerResponse> servers = new ArrayList<>();

        if (!properties.stunUrls().isEmpty()) {
            servers.add(new IceServersResponse.IceServerResponse(properties.stunUrls(), null, null));
        }

        if (properties.hasTurn()) {
            servers.add(new IceServersResponse.IceServerResponse(
                    properties.turnUrls(), properties.turnUsername(), properties.turnPassword()));
        }

        return new IceServersResponse(servers);
    }
}
