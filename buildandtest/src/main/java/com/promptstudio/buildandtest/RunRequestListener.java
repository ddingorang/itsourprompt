package com.promptstudio.buildandtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RunRequestListener {

    private static final Logger log = LoggerFactory.getLogger(RunRequestListener.class);

    private final CodeExecutor codeExecutor;
    private final RabbitTemplate rabbitTemplate;

    public RunRequestListener(CodeExecutor codeExecutor, RabbitTemplate rabbitTemplate) {
        this.codeExecutor = codeExecutor;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RunQueues.REQUEST_QUEUE)
    public void onRequest(RunRequestMessage request) {
        if (request.runId() == null) {
            log.warn("[RUN] runId 없는 요청을 버립니다.");

            return;
        }

        log.info("[RUN] started | runId={} | attemptId={} | files={} | testFiles={}",
                request.runId(), request.attemptId(),
                count(request.files()), count(request.testFiles()));

        RunOutcome outcome = executeSafely(request);

        log.info("[RUN] finished | runId={} | status={} | exitCode={} | duration={} ms",
                request.runId(), outcome.status(), outcome.exitCode(), outcome.durationMs());

        publish(request, outcome);
    }

    /**
     * 어떤 실패든 결과 메시지 한 건으로 귀결시킨다. 예외를 그대로 던지면 메시지가 재전달·DLQ로 가고
     * 백엔드의 run은 QUEUED로 남아 TTL 회수를 기다리게 되므로, 사용자가 원인을 볼 수 없다.
     */
    private int count(List<RunRequestMessage.RunFileMessage> files) {
        return files == null ? 0 : files.size();
    }

    private RunOutcome executeSafely(RunRequestMessage request) {
        try {
            return codeExecutor.execute(request.files(), request.testFiles());
        } catch (RuntimeException exception) {
            log.error("[RUN] 실행 중 예상치 못한 오류 | runId={}", request.runId(), exception);

            return RunOutcome.failure(RunStatus.RUNNER_ERROR,
                    "워커에서 예상치 못한 오류가 발생했습니다: " + exception.getMessage(), 0);
        }
    }

    private void publish(RunRequestMessage request, RunOutcome outcome) {
        rabbitTemplate.convertAndSend(
                RunQueues.EXCHANGE,
                RunQueues.RESULT_ROUTING_KEY,
                new RunResultMessage(
                        request.runId(),
                        outcome.status().name(),
                        outcome.exitCode(),
                        outcome.stdout(),
                        outcome.stderr(),
                        outcome.durationMs()
                )
        );
    }
}
