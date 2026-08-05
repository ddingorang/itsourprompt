package com.promptstudio.attempt.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCallUsageTest {

    private final LlmCallUsage usage = new LlmCallUsage(1, "test-model", 500L, 120L, 300L, 80L, 200L);

    @Test
    void 순번만_바꾼_복사본을_만든다() {
        assertThat(usage.withSeq(3)).isEqualTo(new LlmCallUsage(3, "test-model", 500L, 120L, 300L, 80L, 200L));
    }

    @Test
    void 순번을_바꿔도_원본은_그대로다() {
        usage.withSeq(3);

        assertThat(usage.seq()).isEqualTo(1);
    }
}
