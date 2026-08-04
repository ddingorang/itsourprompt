package com.promptstudio.rabbit;

import com.promptstudio.attempt.domain.CodeRunCase;
import com.promptstudio.attempt.domain.CodeRunCaseStatus;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.service.CodeRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 워커가 돌려준 결과를 어템프트에 반영한다.
 *
 * <p>테스트에서는 {@code promptstudio.rabbit.result-listener-enabled=false}로 이 빈을 아예 만들지 않는다.
 * {@code spring.rabbitmq.listener.simple.auto-startup=false}만으로는 부족하다 —
 * {@code RabbitListenerEndpointRegistry.startIfNecessary}가
 * {@code contextRefreshed || container.isAutoStartup()} 조건이라서, 스프링 테스트가 캐시된 컨텍스트를
 * 재사용하면 contextRefreshed가 이미 true이고 autoStartup 설정이 무시된 채 브로커에 접속을 시도한다.
 */
@Component
@ConditionalOnProperty(
        name = "promptstudio.rabbit.result-listener-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CodeRunResultListener {

    static final String MDC_RUN_ID = "runId";

    private static final Logger log = LoggerFactory.getLogger(CodeRunResultListener.class);

    private final CodeRunService codeRunService;

    public CodeRunResultListener(CodeRunService codeRunService) {
        this.codeRunService = codeRunService;
    }

    @RabbitListener(queues = RunQueues.RESULT_QUEUE)
    public void onResult(RunResultMessage message) {
        if (message.runId() == null) {
            log.warn("[CODE RUN] runId 없는 결과 메시지를 버립니다. status={}", message.status());

            return;
        }

        // 요청 스레드가 아니라 requestId가 없다. runId로 code_run 행을 거쳐 어템프트까지 이어진다.
        // 리스너 컨테이너 스레드는 풀에서 재사용되므로 반드시 지운다 — 안 지우면 다음 메시지에 이전 runId가 붙는다.
        MDC.put(MDC_RUN_ID, message.runId().toString());

        try {
            codeRunService.applyResult(new CodeRunResult(
                    message.runId(),
                    toStatus(message),
                    message.exitCode(),
                    message.stdout(),
                    message.stderr(),
                    message.durationMs(),
                    toCases(message)
            ));
        } finally {
            MDC.remove(MDC_RUN_ID);
        }
    }

    /**
     * 케이스는 보조 자료다. 워커가 안 보냈거나(케이스 기록 이전 버전) 알 수 없는 상태값을 보내도
     * 실행 판정은 건드리지 않고 그 케이스만 버린다 — 상태를 임의로 뭉개면 통과/실패가 뒤집힌다.
     */
    private List<CodeRunCase> toCases(RunResultMessage message) {
        if (message.cases() == null) {
            return List.of();
        }

        List<CodeRunCase> cases = new ArrayList<>();

        for (RunResultMessage.RunCaseMessage source : message.cases()) {
            CodeRunCaseStatus status = toCaseStatus(source, message.runId());

            if (status == null || source.name() == null || source.name().isBlank()) {
                continue;
            }

            cases.add(new CodeRunCase(
                    source.className(), source.name(), status, source.message(), source.durationMs()));
        }

        return cases;
    }

    private CodeRunCaseStatus toCaseStatus(RunResultMessage.RunCaseMessage source, UUID runId) {
        try {
            return CodeRunCaseStatus.valueOf(source.status());
        } catch (IllegalArgumentException | NullPointerException exception) {
            log.warn("[CODE RUN] 알 수 없는 케이스 상태값을 버립니다. runId={} | name={} | status={}",
                    runId, source.name(), source.status());

            return null;
        }
    }

    /**
     * 워커가 백엔드보다 먼저 배포되어 모르는 상태값을 보낼 수 있다. 그때 재시도 루프에 갇히지 않도록
     * RUNNER_ERROR로 낮춰 기록한다 — 실행 자체는 이미 끝났으므로 되돌릴 것이 없다.
     */
    private CodeRunStatus toStatus(RunResultMessage message) {
        try {
            CodeRunStatus status = CodeRunStatus.valueOf(message.status());

            if (status == CodeRunStatus.QUEUED) {
                log.warn("[CODE RUN] 워커가 QUEUED를 결과로 보냈습니다. runId={}", message.runId());

                return CodeRunStatus.RUNNER_ERROR;
            }

            return status;
        } catch (IllegalArgumentException | NullPointerException exception) {
            log.warn("[CODE RUN] 알 수 없는 상태값입니다. runId={} | status={}", message.runId(), message.status());

            return CodeRunStatus.RUNNER_ERROR;
        }
    }
}
