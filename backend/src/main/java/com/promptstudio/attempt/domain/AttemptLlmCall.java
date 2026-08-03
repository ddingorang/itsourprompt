package com.promptstudio.attempt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * LLM API 호출 1건. 원본 행이 진실이고 총계는 조회 시 SUM으로 파생한다.
 *
 * <p>Turn과 연관을 맺지 않는 독립 엔티티다 — 턴이 저장되지 않은 실패 호출도 남겨야 하고, 과금 기록의
 * 수명을 턴의 orphanRemoval에 묶지 않기 위해서다. 턴 연결은 (attempt_id, turn_ordinal)로 한다.
 */
@Entity
@Table(name = "attempt_llm_call")
public class AttemptLlmCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    /**
     * 피드백 호출과 실패 flush 행은 턴에 속하지 않아 null이다.
     */
    @Column(name = "turn_ordinal")
    private Integer turnOrdinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private LlmCallPurpose purpose;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "model")
    private String model;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cached_input_tokens")
    private Long cachedInputTokens;

    @Column(name = "reasoning_tokens")
    private Long reasoningTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    /**
     * 쓰기 시점 단가로 계산한 USD. 단가를 모르는 모델은 null이다.
     */
    @Column(name = "cost", precision = 14, scale = 8)
    private BigDecimal cost;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LlmCallStatus status;

    @Column(name = "error_type")
    private String errorType;

    /**
     * 실패한 CODE 호출의 사용자 프롬프트. 성공 행은 턴에 입력이 남으므로 null이다.
     *
     * <p>FEEDBACK 실패 행이 null인 것은 기록 누락이 아니다 — 피드백 프롬프트는 사용자가 타이핑한 값이 아니라
     * 어댑터가 problem과 attempt로 만들어내는 산출물이고, 실패하면 제출이 되지 않아 그 입력이
     * 어템프트에 그대로 남아 있다.
     */
    @Column(name = "user_prompt", columnDefinition = "text")
    private String userPrompt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AttemptLlmCall() {
    }

    private AttemptLlmCall(Long attemptId, Integer turnOrdinal, LlmCallPurpose purpose, int seq) {
        this.attemptId = attemptId;
        this.turnOrdinal = turnOrdinal;
        this.purpose = purpose;
        this.seq = seq;
        this.createdAt = Instant.now();
    }

    public static AttemptLlmCall success(
            Long attemptId,
            Integer turnOrdinal,
            LlmCallPurpose purpose,
            LlmCallUsage usage,
            BigDecimal cost
    ) {
        AttemptLlmCall call = new AttemptLlmCall(attemptId, turnOrdinal, purpose, usage.seq());

        call.model = usage.model();
        call.inputTokens = usage.inputTokens();
        call.outputTokens = usage.outputTokens();
        call.cachedInputTokens = usage.cachedInputTokens();
        call.reasoningTokens = usage.reasoningTokens();
        call.latencyMs = usage.latencyMs();
        call.cost = cost;
        call.status = LlmCallStatus.SUCCESS;

        return call;
    }

    /**
     * 응답을 받지 못한 호출은 토큰을 알 수 없다 — 실패 사실과 분류, 그리고 어디에도 남지 않는 입력만 남긴다.
     */
    public static AttemptLlmCall failed(
            Long attemptId,
            LlmCallPurpose purpose,
            int seq,
            String errorType,
            String userPrompt
    ) {
        AttemptLlmCall call = new AttemptLlmCall(attemptId, null, purpose, seq);

        call.status = LlmCallStatus.FAILED;
        call.errorType = errorType;
        call.userPrompt = userPrompt;

        return call;
    }

    public Long id() {
        return id;
    }

    public Long attemptId() {
        return attemptId;
    }

    public Integer turnOrdinal() {
        return turnOrdinal;
    }

    public LlmCallPurpose purpose() {
        return purpose;
    }

    public int seq() {
        return seq;
    }

    public String model() {
        return model;
    }

    public Long inputTokens() {
        return inputTokens;
    }

    public Long outputTokens() {
        return outputTokens;
    }

    public Long cachedInputTokens() {
        return cachedInputTokens;
    }

    public Long reasoningTokens() {
        return reasoningTokens;
    }

    public Long latencyMs() {
        return latencyMs;
    }

    public BigDecimal cost() {
        return cost;
    }

    public LlmCallStatus status() {
        return status;
    }

    public String errorType() {
        return errorType;
    }

    public String userPrompt() {
        return userPrompt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttemptLlmCall call)) {
            return false;
        }

        return id != null && id.equals(call.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
