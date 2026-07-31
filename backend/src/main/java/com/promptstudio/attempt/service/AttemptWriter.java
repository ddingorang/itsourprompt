package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.repository.AttemptRepository;
import com.promptstudio.attempt.repository.IdempotencyRepository;
import com.promptstudio.problem.domain.Problem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어템프트를 변경하는 트랜잭션 경계. 엔티티는 이 클래스 밖으로 나가지 않는다.
 * AI 호출처럼 오래 걸리는 작업은 여기 들어오기 전에 끝나 있어야 한다.
 */
@Component
class AttemptWriter {

    private final AttemptRepository attemptRepository;
    private final IdempotencyRepository idempotencyRepository;

    AttemptWriter(AttemptRepository attemptRepository, IdempotencyRepository idempotencyRepository) {
        this.attemptRepository = attemptRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional
    AttemptView start(Problem problem, String idempotencyKey) {
        Attempt attempt = attemptRepository.save(Attempt.start(problem));

        markCompleted(idempotencyKey, attempt.id());

        return AttemptView.from(attempt);
    }

    @Transactional
    AttemptView appendTurn(Long attemptId, String userPrompt, GeneratedCode generated, String idempotencyKey) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));

        attempt.applyTurn(userPrompt, generated);
        markCompleted(idempotencyKey, attemptId);

        return AttemptView.from(attempt);
    }

    @Transactional
    AttemptView submit(Long attemptId, AttemptFeedback feedback) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));

        // AI 호출 중 다른 요청이 먼저 제출을 끝냈다면 저장된 피드백을 그대로 반환한다.
        if (attempt.status() != AttemptStatus.SUBMITTED) {
            attempt.submit(feedback);
        }

        return AttemptView.from(attempt);
    }

    private void markCompleted(String idempotencyKey, Long attemptId) {
        if (idempotencyKey != null) {
            idempotencyRepository.markCompleted(idempotencyKey, attemptId);
        }
    }
}
