package com.promptstudio.buildandtest;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 컨버터만 등록한다. 큐·익스체인지 선언은 백엔드가 단독으로 하므로 여기서는 하지 않는다
 * (양쪽이 선언하면 속성 불일치 시 PRECONDITION_FAILED가 난다).
 *
 * <p>기본 TypePrecedence가 INFERRED라서 백엔드가 보낸 {@code __TypeId__} 헤더 대신
 * 리스너 메서드 시그니처의 타입으로 역직렬화된다. 패키지명이 서로 달라도 동작하는 근거다.
 */
@Configuration
public class WorkerRabbitConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
