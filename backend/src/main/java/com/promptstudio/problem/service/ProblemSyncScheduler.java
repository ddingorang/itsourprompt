package com.promptstudio.problem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ProblemSyncScheduler.class);

    private final ProblemSyncService problemSyncService;

    public ProblemSyncScheduler(ProblemSyncService problemSyncService) {
        this.problemSyncService = problemSyncService;
    }

    @Scheduled(initialDelayString = "0", fixedDelayString = "${PROBLEM_SYNC_INTERVAL_MS:300000}")
    public void sync() {
        try {
            ProblemSyncService.SyncResult result = problemSyncService.sync();

            if (!result.skipped()) {
                log.info(
                        "문제 동기화 완료: created={}, updated={}, deactivated={}",
                        result.created(),
                        result.updated(),
                        result.deactivated()
                );
            }
        } catch (Exception exception) {
            log.error("문제 동기화에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        }
    }
}
