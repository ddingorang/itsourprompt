package com.promptstudio.rabbit;

import com.promptstudio.problem.domain.ProblemFile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitCodeRunPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitCodeRunPublisher publisher = new RabbitCodeRunPublisher(rabbitTemplate);

    @Test
    void 문제의_언어가_라우팅_키를_정하고_메시지에도_실린다() {
        UUID runId = UUID.randomUUID();

        publisher.publish(runId, 7L, "python",
                List.of(new ProblemFile("src/main/python/main.py", "print('hi')")), List.of());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RunQueues.EXCHANGE), eq(RunQueues.REQUEST_QUEUE_PYTHON), payload.capture());

        RunRequestMessage message = (RunRequestMessage) payload.getValue();
        assertThat(message.runId()).isEqualTo(runId);
        assertThat(message.language()).isEqualTo("python");
    }

    @Test
    void java_요청은_java_큐로_발행된다() {
        publisher.publish(UUID.randomUUID(), 7L, "java", List.of(), List.of());

        verify(rabbitTemplate).convertAndSend(
                eq(RunQueues.EXCHANGE), eq(RunQueues.REQUEST_QUEUE_JAVA), (Object) org.mockito.ArgumentMatchers.any());
    }
}
