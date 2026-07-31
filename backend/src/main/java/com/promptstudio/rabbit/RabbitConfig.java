package com.promptstudio.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 실행 잡 토폴로지를 백엔드가 단독으로 선언한다.
 *
 * <p>워커도 같은 큐를 선언하면 속성이 조금이라도 다를 때 PRECONDITION_FAILED 채널 예외가 나므로,
 * 선언자는 한 쪽이어야 한다. 워커는 선언하지 않고 missing-queues-fatal=false로 큐가 생길 때까지 재시도한다.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange runExchange() {
        return new DirectExchange(RunQueues.EXCHANGE);
    }

    @Bean
    public DirectExchange runDeadLetterExchange() {
        return new DirectExchange(RunQueues.DEAD_LETTER_EXCHANGE);
    }

    /**
     * 역직렬화 불가 등으로 되살릴 수 없는 메시지는 DLQ로 보낸다. 재시도 루프에 갇히는 것을 막는다.
     */
    @Bean
    public Queue runRequestQueue() {
        return QueueBuilder.durable(RunQueues.REQUEST_QUEUE)
                .deadLetterExchange(RunQueues.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RunQueues.REQUEST_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue runRequestDeadLetterQueue() {
        return QueueBuilder.durable(RunQueues.REQUEST_DLQ).build();
    }

    @Bean
    public Queue runResultQueue() {
        return QueueBuilder.durable(RunQueues.RESULT_QUEUE).build();
    }

    @Bean
    public Binding runRequestBinding() {
        return BindingBuilder.bind(runRequestQueue())
                .to(runExchange())
                .with(RunQueues.REQUEST_ROUTING_KEY);
    }

    @Bean
    public Binding runRequestDeadLetterBinding() {
        return BindingBuilder.bind(runRequestDeadLetterQueue())
                .to(runDeadLetterExchange())
                .with(RunQueues.REQUEST_DLQ_ROUTING_KEY);
    }

    @Bean
    public Binding runResultBinding() {
        return BindingBuilder.bind(runResultQueue())
                .to(runExchange())
                .with(RunQueues.RESULT_ROUTING_KEY);
    }

    /**
     * 발행 시 {@code __TypeId__} 헤더에 발신 측 클래스명이 실리지만, 이 컨버터의 기본
     * TypePrecedence는 INFERRED라서 수신 측은 리스너 메서드 시그니처의 타입으로 역직렬화한다.
     * 백엔드와 워커의 record 패키지가 달라도 동작하는 근거이므로 TYPE_ID로 바꾸면 안 된다.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
