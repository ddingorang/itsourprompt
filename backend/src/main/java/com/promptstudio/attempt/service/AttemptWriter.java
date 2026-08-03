package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptLlmCall;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.domain.LlmCallPurpose;
import com.promptstudio.attempt.domain.LlmCallUsage;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.repository.AttemptRepository;
import com.promptstudio.attempt.repository.IdempotencyRepository;
import com.promptstudio.attempt.repository.LlmCallRepository;
import com.promptstudio.problem.domain.Problem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 어템프트를 변경하는 트랜잭션 경계. 엔티티는 이 클래스 밖으로 나가지 않는다.
 * AI 호출처럼 오래 걸리는 작업은 여기 들어오기 전에 끝나 있어야 한다.
 */
@Component
class AttemptWriter {

    private final AttemptRepository attemptRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final LlmCallRepository llmCallRepository;
    private final LlmCostCalculator costCalculator;

    AttemptWriter(
            AttemptRepository attemptRepository,
            IdempotencyRepository idempotencyRepository,
            LlmCallRepository llmCallRepository,
            LlmCostCalculator costCalculator
    ) {
        this.attemptRepository = attemptRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.llmCallRepository = llmCallRepository;
        this.costCalculator = costCalculator;
    }

    @Transactional
    AttemptView start(Problem problem, AttemptOwner owner, String idempotencyKey) {
        Attempt attempt = attemptRepository.save(Attempt.start(problem, owner));

        markCompleted(idempotencyKey, attempt.id());

        return AttemptView.from(attempt);
    }

    /**
     * 턴 저장과 사용량 기록을 한 커밋으로 묶는다 — 턴은 남았는데 과금 기록이 없는 상태를 만들지 않는다.
     */
    @Transactional
    AttemptView appendTurn(Long attemptId, AttemptOwner owner, String userPrompt, GeneratedCode generated, String idempotencyKey) {
        Attempt attempt = findAttempt(attemptId, owner)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));

        attempt.applyTurn(userPrompt, generated);

        int ordinal = attempt.turns().size() - 1;
        llmCallRepository.saveAll(successCalls(attemptId, ordinal, LlmCallPurpose.CODE, generated.llmCalls()));
        markCompleted(idempotencyKey, attemptId);

        return AttemptView.from(attempt);
    }

    @Transactional
    AttemptView submit(Long attemptId, AttemptOwner owner, AttemptFeedback feedback) {
        Attempt attempt = findAttempt(attemptId, owner)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));

        // AI 호출 중 다른 요청이 먼저 제출을 끝냈다면 저장된 피드백을 그대로 반환한다.
        if (attempt.status() != AttemptStatus.SUBMITTED) {
            attempt.submit(feedback);
        }

        // 피드백이 채택되지 않았어도 호출은 이미 토큰을 썼으므로 기록한다.
        llmCallRepository.saveAll(successCalls(attemptId, null, LlmCallPurpose.FEEDBACK, feedback.llmCalls()));

        return AttemptView.from(attempt);
    }

    /**
     * 실패로 턴이 저장되지 않아도 이미 끝난 호출의 토큰은 과금된다 — turn_ordinal 없이 남기고
     * 실패 자체는 마커 1행으로 표시한다. 호출자의 실패와 별개 트랜잭션이라 원 예외가 롤백해도 남는다.
     * 마커 행에는 턴이 저장되지 않아 어디에도 남지 않는 사용자 입력도 함께 싣는다.
     */
    @Transactional
    void recordFailure(
            Long attemptId,
            LlmCallPurpose purpose,
            List<LlmCallUsage> calls,
            String errorType,
            String userPrompt
    ) {
        List<AttemptLlmCall> rows = new ArrayList<>(successCalls(attemptId, null, purpose, calls));

        rows.add(AttemptLlmCall.failed(attemptId, purpose, calls.size() + 1, errorType, userPrompt));

        llmCallRepository.saveAll(rows);
    }

    private List<AttemptLlmCall> successCalls(
            Long attemptId,
            Integer turnOrdinal,
            LlmCallPurpose purpose,
            List<LlmCallUsage> calls
    ) {
        List<AttemptLlmCall> rows = new ArrayList<>();

        for (LlmCallUsage usage : calls) {
            rows.add(AttemptLlmCall.success(attemptId, turnOrdinal, purpose, usage, costCalculator.costOf(usage)));
        }

        return rows;
    }

    private void markCompleted(String idempotencyKey, Long attemptId) {
        if (idempotencyKey != null) {
            idempotencyRepository.markCompleted(idempotencyKey, attemptId);
        }
    }

    private java.util.Optional<Attempt> findAttempt(Long attemptId, AttemptOwner owner) {
        if (owner.isUser()) {
            return attemptRepository.findByIdAndUserId(attemptId, owner.userId());
        }
        return attemptRepository.findByIdAndGuestSessionId(attemptId, owner.guestSessionId());
    }
}
