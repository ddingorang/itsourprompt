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
        // 언어가 라우팅 키를 정한다. 메시지에도 language를 계속 싣는다 — DLQ에 빠진 메시지를
        // 조사할 때 어느 언어였는지가 메시지 자체에 남아야 하고, 워커의 구성 오류 방어에도 쓰인다.
        rabbitTemplate.convertAndSend(
                RunQueues.EXCHANGE,
                RunQueues.requestRoutingKey(language),
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
