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
 *
 * <p>요청 큐는 언어별이고 DLQ도 큐별로 둔다 — 죽은 메시지가 어느 언어의 것이었는지가
 * 큐 이름만으로 드러나야 조사가 쉽다.
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
    public Queue runRequestJavaQueue() {
        return requestQueue(RunQueues.REQUEST_QUEUE_JAVA, RunQueues.REQUEST_DLQ_JAVA);
    }

    @Bean
    public Queue runRequestPythonQueue() {
        return requestQueue(RunQueues.REQUEST_QUEUE_PYTHON, RunQueues.REQUEST_DLQ_PYTHON);
    }

    /**
     * 언어 도입 전의 요청 큐. 구버전이 발행해 둔 메시지를 java 워커가 마저 비우는 이행기 동안만
     * 선언을 유지한다 — 빈 것을 확인하면 이 선언과 바인딩, 워커의 구독을 함께 제거한다.
     */
    @Bean
    public Queue runRequestLegacyQueue() {
        return requestQueue(RunQueues.LEGACY_REQUEST_QUEUE, RunQueues.LEGACY_REQUEST_DLQ);
    }

    private Queue requestQueue(String name, String deadLetterRoutingKey) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(RunQueues.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean
    public Queue runRequestJavaDeadLetterQueue() {
        return QueueBuilder.durable(RunQueues.REQUEST_DLQ_JAVA).build();
    }

    @Bean
    public Queue runRequestPythonDeadLetterQueue() {
        return QueueBuilder.durable(RunQueues.REQUEST_DLQ_PYTHON).build();
    }

    @Bean
    public Queue runRequestLegacyDeadLetterQueue() {
        return QueueBuilder.durable(RunQueues.LEGACY_REQUEST_DLQ).build();
    }

    @Bean
    public Queue runResultQueue() {
        return QueueBuilder.durable(RunQueues.RESULT_QUEUE).build();
    }

    @Bean
    public Binding runRequestJavaBinding() {
        return BindingBuilder.bind(runRequestJavaQueue()).to(runExchange()).with(RunQueues.REQUEST_QUEUE_JAVA);
    }

    @Bean
    public Binding runRequestPythonBinding() {
        return BindingBuilder.bind(runRequestPythonQueue()).to(runExchange()).with(RunQueues.REQUEST_QUEUE_PYTHON);
    }

    @Bean
    public Binding runRequestLegacyBinding() {
        return BindingBuilder.bind(runRequestLegacyQueue()).to(runExchange()).with(RunQueues.LEGACY_REQUEST_QUEUE);
    }

    @Bean
    public Binding runRequestJavaDeadLetterBinding() {
        return BindingBuilder.bind(runRequestJavaDeadLetterQueue())
                .to(runDeadLetterExchange())
                .with(RunQueues.REQUEST_DLQ_JAVA);
    }

    @Bean
    public Binding runRequestPythonDeadLetterBinding() {
        return BindingBuilder.bind(runRequestPythonDeadLetterQueue())
                .to(runDeadLetterExchange())
                .with(RunQueues.REQUEST_DLQ_PYTHON);
    }

    @Bean
    public Binding runRequestLegacyDeadLetterBinding() {
        return BindingBuilder.bind(runRequestLegacyDeadLetterQueue())
                .to(runDeadLetterExchange())
                .with(RunQueues.LEGACY_REQUEST_DLQ);
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
