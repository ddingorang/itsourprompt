package com.promptstudio.problem.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 5분 주기 배치가 남기는 로그가 사람 트래픽의 에러를 덮지 않는지 확인한다.
 */
class ProblemSyncSchedulerTest {

    private static final ProblemSyncService.SyncResult SYNCED =
            new ProblemSyncService.SyncResult(1, 0, 0, false);

    private final ProblemSyncService problemSyncService = mock(ProblemSyncService.class);
    private final ProblemSyncScheduler scheduler = new ProblemSyncScheduler(problemSyncService);

    private ch.qos.logback.classic.Logger schedulerLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void 로그를_수집한다() {
        schedulerLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProblemSyncScheduler.class);
        appender = new ListAppender<>();
        appender.start();
        schedulerLogger.addAppender(appender);
    }

    @AfterEach
    void 정리한다() {
        schedulerLogger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void 동기화_중에는_job_컨텍스트가_실리고_끝나면_지워진다() {
        AtomicReference<String> job = new AtomicReference<>();
        when(problemSyncService.sync()).thenAnswer(invocation -> {
            job.set(MDC.get(ProblemSyncScheduler.MDC_JOB));

            return SYNCED;
        });

        scheduler.sync();

        assertThat(job.get()).isEqualTo(ProblemSyncScheduler.JOB_NAME);
        assertThat(MDC.get(ProblemSyncScheduler.MDC_JOB)).isNull();
    }

    @Test
    void 첫_실패만_ERROR고_이어지는_실패는_WARN으로_내린다() {
        when(problemSyncService.sync()).thenThrow(new IllegalStateException("토큰이 만료되었습니다."));

        scheduler.sync();
        scheduler.sync();
        scheduler.sync();

        assertThat(appender.list).extracting(ILoggingEvent::getLevel)
                .containsExactly(Level.ERROR, Level.WARN, Level.WARN);
        // 첫 실패에만 스택트레이스를 남긴다.
        assertThat(appender.list.getFirst().getThrowableProxy()).isNotNull();
        assertThat(appender.list.getLast().getThrowableProxy()).isNull();
    }

    @Test
    void 복구되면_INFO_한_줄을_남긴다() {
        when(problemSyncService.sync())
                .thenThrow(new IllegalStateException("토큰이 만료되었습니다."))
                .thenReturn(SYNCED);

        scheduler.sync();
        appender.list.clear();
        scheduler.sync();

        assertThat(appender.list).extracting(ILoggingEvent::getLevel)
                .containsOnly(Level.INFO);
        assertThat(appender.list.getFirst().getMessage()).contains("복구");
    }
}
