package com.promptstudio.relay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 클라이언트에 내려줄 ICE 서버 설정.
 *
 * @param stunUrls 주소 발견용. 자격증명이 없다
 * @param turnUrls 중계용. 비어 있으면 TURN 없이 STUN만 내려간다 — 로컬 개발이 이 상태다
 * @param turnUsername coturn의 정적 계정(--user). 시간제한 HMAC 자격증명으로 바꾸면
 *                     이 속성 대신 백엔드가 만료 있는 크리덴셜을 만들어 내려주게 된다
 */
@ConfigurationProperties(prefix = "relay.webrtc")
public record RelayWebRtcProperties(
        List<String> stunUrls,
        List<String> turnUrls,
        String turnUsername,
        String turnPassword
) {

    public RelayWebRtcProperties {
        // 빈 환경변수(RELAY_TURN_URLS=)가 [""]로 바인딩되면 hasTurn()이 참이 되어
        // 자격증명 없는 빈 TURN 항목이 내려간다 — 공백 항목은 없는 것으로 취급한다.
        stunUrls = withoutBlanks(stunUrls);
        turnUrls = withoutBlanks(turnUrls);
    }

    private static List<String> withoutBlanks(List<String> urls) {
        if (urls == null) {
            return List.of();
        }

        return urls.stream().filter(url -> url != null && !url.isBlank()).toList();
    }

    public boolean hasTurn() {
        return !turnUrls.isEmpty();
    }
}
