package com.promptstudio.relay.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.List;

/**
 * 릴레이 소켓 등록.
 *
 * <p>STOMP를 쓰지 않는다. 필요한 것은 방 단위 브로드캐스트와 시그널링 중계뿐이고, STOMP를 얹으면
 * 브로커·구독 의미론과 클라이언트 의존성이 함께 따라온다.
 *
 * <p>허용 오리진은 REST와 같은 설정({@code app.cors.allowed-origins})을 읽는다. 핸드셰이크의
 * Origin 검사는 CORS 매핑({@code /api/**})이 대신해 주지 않으므로 여기서 따로 지정해야 하고,
 * 지정하지 않으면 스프링 기본값이 same-origin만 허용해 로컬 개발 서버(5173)가 붙지 못한다.
 */
@Configuration
@EnableWebSocket
public class RelayWebSocketConfig implements WebSocketConfigurer {

    static final String PATH = "/ws/relay/*";

    private final RelayWebSocketHandler relayWebSocketHandler;
    private final List<String> allowedOrigins;

    public RelayWebSocketConfig(
            RelayWebSocketHandler relayWebSocketHandler,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins
    ) {
        this.relayWebSocketHandler = relayWebSocketHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(relayWebSocketHandler, PATH)
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    /**
     * 수신 텍스트 버퍼를 넉넉히 잡는다. 톰캣 기본은 8KB인데 시그널링으로 오가는 SDP는 후보가
     * 많으면 그보다 커진다 — 기본값이면 offer가 조용히 잘리고 연결이 성립하지 않는다.
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);

        return container;
    }
}
