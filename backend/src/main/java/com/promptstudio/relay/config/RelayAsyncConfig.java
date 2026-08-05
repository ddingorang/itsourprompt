package com.promptstudio.relay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RelayAsyncConfig {

    /**
     * 피드백 생성(LLM, 수십 초) 전용. 트리거 지점이 rabbit 리스너·스케줄러 스레드라 거기서
     * 블로킹하면 다른 방의 채점 결과 처리가 밀린다.
     *
     * <p>풀이 작다 — 피드백은 방 하나가 게임을 다 끝냈을 때 한 번이다. 큐까지 차면(동시 종료
     * 12방 이상) 호출 스레드가 직접 실행하는 대신 거절되는데, 그때는 feedback.failed 없이
     * 조용히 실패하므로 재시도 API가 회수 수단이다.
     */
    @Bean(name = "relayFeedbackExecutor")
    public ThreadPoolTaskExecutor relayFeedbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("relay-feedback-");

        return executor;
    }
}
