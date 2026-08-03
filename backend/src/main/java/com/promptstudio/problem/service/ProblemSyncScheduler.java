package com.promptstudio.problem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 부트 직후 한 번, 그 뒤로는 주기적으로 문제 저장소를 동기화한다.
 *
 * <p>동기화 실패로 기동이나 서비스가 멈추면 안 되므로 예외는 로그만 남기고 삼킨다. 다음 주기에 다시 시도한다.
 */
@Component
@ConditionalOnProperty(name = "PROBLEM_SYNC_ENABLED", havingValue = "true", matchIfMissing = true)
public class ProblemSyncScheduler {

    static final String MDC_JOB = "job";
    static final String JOB_NAME = "problem-sync";

    private static final Logger log = LoggerFactory.getLogger(ProblemSyncScheduler.class);

    private final ProblemSyncService problemSyncService;

    /**
     * fixedDelay라 중첩 실행이 없어 원자적 갱신이 필요 없다. 재스케줄이 다른 스레드에 실릴 때의
     * 가시성만 있으면 되므로 volatile로 충분하다.
     */
    private volatile int consecutiveFailures;

    public ProblemSyncScheduler(ProblemSyncService problemSyncService) {
        this.problemSyncService = problemSyncService;
    }

    @Scheduled(initialDelayString = "0", fixedDelayString = "${PROBLEM_SYNC_INTERVAL_MS:300000}")
    public void sync() {
        // 사람 트래픽 에러만 보려면 배치가 남긴 줄을 걸러낼 키가 있어야 한다.
        MDC.put(MDC_JOB, JOB_NAME);

        try {
            ProblemSyncService.SyncResult result = problemSyncService.sync();

            if (consecutiveFailures > 0) {
                log.atInfo()
                        .addKeyValue("consecutiveFailures", consecutiveFailures)
                        .log("문제 동기화가 연속 실패에서 복구되었습니다.");

                consecutiveFailures = 0;
            }

            if (!result.skipped()) {
                log.info(
                        "문제 동기화 완료: created={}, updated={}, deactivated={}",
                        result.created(),
                        result.updated(),
                        result.deactivated()
                );
            }
        } catch (Exception exception) {
            consecutiveFailures++;

            // 5분 주기라 실패가 지속되면 하루 288줄의 ERROR가 쌓여 진짜 에러를 덮는다.
            // 첫 실패만 ERROR로 남겨 실패가 시작된 시각을 선명하게 두고, 이어지는 실패는 WARN으로 내린다.
            if (consecutiveFailures == 1) {
                log.error("문제 동기화에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
            } else {
                log.atWarn()
                        .addKeyValue("consecutiveFailures", consecutiveFailures)
                        .log("문제 동기화 실패가 계속됩니다. 다음 주기에 다시 시도합니다.");
            }
        } finally {
            MDC.remove(MDC_JOB);
        }
    }
}
