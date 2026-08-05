package com.promptstudio.rabbit;

import com.promptstudio.attempt.port.CodeRunPublisher;
import com.promptstudio.problem.domain.ProblemFile;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class RabbitCodeRunPublisher implements CodeRunPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitCodeRunPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(UUID runId, Long attemptId, String language, List<ProblemFile> files, List<ProblemFile> testFiles) {
        rabbitTemplate.convertAndSend(
                RunQueues.EXCHANGE,
                RunQueues.REQUEST_ROUTING_KEY,
                new RunRequestMessage(runId, attemptId, language, toPayload(files), toPayload(testFiles))
        );
    }

    private List<RunRequestMessage.RunFileMessage> toPayload(List<ProblemFile> files) {
        List<RunRequestMessage.RunFileMessage> payload = new ArrayList<>();

        for (ProblemFile file : files) {
            payload.add(new RunRequestMessage.RunFileMessage(file.path(), file.content()));
        }

        return payload;
    }
}
