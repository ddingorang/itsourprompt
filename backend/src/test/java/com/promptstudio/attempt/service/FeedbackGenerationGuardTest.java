package com.promptstudio.attempt.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackGenerationGuardTest {

    private final FeedbackGenerationGuard guard = new FeedbackGenerationGuard();

    @Test
    void 처음_획득은_성공하고_생성_중_재획득은_실패한다() {
        assertThat(guard.tryAcquire(1L)).isTrue();

        assertThat(guard.tryAcquire(1L)).isFalse();
        assertThat(guard.isGenerating(1L)).isTrue();
    }

    @Test
    void 해제하면_다시_획득할_수_있다() {
        guard.tryAcquire(1L);

        guard.release(1L);

        assertThat(guard.isGenerating(1L)).isFalse();
        assertThat(guard.tryAcquire(1L)).isTrue();
    }

    @Test
    void 다른_어템프트의_획득에_영향을_주지_않는다() {
        guard.tryAcquire(1L);

        assertThat(guard.tryAcquire(2L)).isTrue();
        assertThat(guard.isGenerating(2L)).isTrue();
    }
}
