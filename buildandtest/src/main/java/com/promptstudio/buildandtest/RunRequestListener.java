package com.promptstudio.buildandtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RunRequestListener {

    private static final Logger log = LoggerFactory.getLogger(RunRequestListener.class);

    private final CodeExecutor codeExecutor;
    private final RabbitTemplate rabbitTemplate;
    private final String workerLanguage;

    public RunRequestListener(
            CodeExecutor codeExecutor,
            RabbitTemplate rabbitTemplate,
            @Value("${worker.language:java}") String workerLanguage
    ) {
        this.codeExecutor = codeExecutor;
        this.rabbitTemplate = rabbitTemplate;
        this.workerLanguage = workerLanguage;
    }

    @RabbitListener(queues = "#{'${worker.request-queues}'.split(',')}")
    public void onRequest(RunRequestMessage request) {
        if (request.runId() == null) {
            log.warn("[RUN] runId 없는 요청을 버립니다.");

            return;
        }

        // 언어 불일치는 큐 바인딩·워커 구성이 어긋난 것이다. 그대로 실행하면 "javac가 .py를
        // 컴파일하다 COMPILE_ERROR" 같은 그럴듯한 오채점이 되므로, 명확한 워커 오류로 돌려보낸다.
        String requestedLanguage = request.language() == null ? "java" : request.language();

        if (!workerLanguage.equals(requestedLanguage)) {
            log.error("[RUN] 언어가 다른 요청이 도착했습니다 | runId={} | worker={} | requested={}",
                    request.runId(), workerLanguage, requestedLanguage);

            publish(request, RunOutcome.failure(RunStatus.RUNNER_ERROR,
                    "워커 구성 오류: 이 워커는 " + workerLanguage + " 전용인데 "
                            + requestedLanguage + " 요청이 도착했습니다.", 0));

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
                        outcome.durationMs(),
                        toCaseMessages(outcome.cases())
                )
        );
    }

    private List<RunResultMessage.RunCaseMessage> toCaseMessages(List<RunCase> cases) {
        List<RunResultMessage.RunCaseMessage> messages = new ArrayList<>();

        for (RunCase testCase : cases) {
            messages.add(new RunResultMessage.RunCaseMessage(
                    testCase.className(),
                    testCase.name(),
                    testCase.status().name(),
                    testCase.message(),
                    testCase.durationMs()
            ));
        }

        return messages;
    }
}
