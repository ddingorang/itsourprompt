package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.exception.AttemptAlreadySubmittedException;
import com.promptstudio.attempt.exception.AttemptHasNoTurnsException;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.exception.FeedbackGenerationInProgressException;
import com.promptstudio.attempt.exception.FeedbackNotFoundException;
import com.promptstudio.attempt.port.CodeGenerator;
import com.promptstudio.attempt.port.FeedbackGenerator;
import com.promptstudio.attempt.repository.AttemptQueryRepository;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemView;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class AttemptService {

    private final ProblemRepository problemRepository;
    private final AttemptQueryRepository attemptQueryRepository;
    private final AttemptWriter attemptWriter;
    private final IdempotencyGuard idempotencyGuard;
    private final FeedbackGenerationGuard feedbackGenerationGuard;
    private final CodeGenerator codeGenerator;
    private final FeedbackGenerator feedbackGenerator;

    public AttemptService(
            ProblemRepository problemRepository,
            AttemptQueryRepository attemptQueryRepository,
            AttemptWriter attemptWriter,
            IdempotencyGuard idempotencyGuard,
            FeedbackGenerationGuard feedbackGenerationGuard,
            CodeGenerator codeGenerator,
            FeedbackGenerator feedbackGenerator
    ) {
        this.problemRepository = problemRepository;
        this.attemptQueryRepository = attemptQueryRepository;
        this.attemptWriter = attemptWriter;
        this.idempotencyGuard = idempotencyGuard;
        this.feedbackGenerationGuard = feedbackGenerationGuard;
        this.codeGenerator = codeGenerator;
        this.feedbackGenerator = feedbackGenerator;
    }

    public AttemptView startAttempt(Long problemId) {
        return startAttempt(problemId, null);
    }

    public AttemptView startAttempt(Long problemId, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);

        if (key == null) {
            return attemptWriter.start(getProblem(problemId), null);
        }

        return withIdempotency(key, () -> attemptWriter.start(getProblem(problemId), key));
    }

    public AttemptView getAttempt(Long attemptId) {
        return attemptQueryRepository.findById(attemptId)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    public String getFeedback(Long attemptId) {
        AttemptView attempt = getAttempt(attemptId);

        if (attempt.status() != AttemptStatus.SUBMITTED) {
            throw new FeedbackNotFoundException(attemptId);
        }

        return attempt.feedback();
    }

    public AttemptView addTurn(Long attemptId, String userPrompt) {
        return addTurn(attemptId, userPrompt, null);
    }

    public AttemptView addTurn(Long attemptId, String userPrompt, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);

        if (key == null) {
            return generateTurn(attemptId, userPrompt, null);
        }

        return withIdempotency(key, () -> generateTurn(attemptId, userPrompt, key));
    }

    private AttemptView generateTurn(Long attemptId, String userPrompt, String idempotencyKey) {
        if (feedbackGenerationGuard.isGenerating(attemptId)) {
            throw new FeedbackGenerationInProgressException(attemptId);
        }

        AttemptView attempt = getAttempt(attemptId);

        if (attempt.status() == AttemptStatus.SUBMITTED) {
            throw new AttemptAlreadySubmittedException(attemptId);
        }

        GeneratedCode generated = codeGenerator.generate(attempt, userPrompt);

        return attemptWriter.appendTurn(attemptId, userPrompt, generated, idempotencyKey);
    }

    private AttemptView withIdempotency(String key, Supplier<AttemptView> action) {
        return switch (idempotencyGuard.reserve(key)) {
            case IdempotencyGuard.Reservation.Replay(Long attemptId) -> getAttempt(attemptId);
            case IdempotencyGuard.Reservation.Acquired ignored -> runOwning(key, action);
        };
    }

    private AttemptView runOwning(String key, Supplier<AttemptView> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            try {
                idempotencyGuard.release(key);
            } catch (RuntimeException releaseFailure) {
                e.addSuppressed(releaseFailure);
            }

            throw e;
        }
    }

    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }

        return idempotencyKey;
    }

    public String submit(Long attemptId) {
        if (!feedbackGenerationGuard.tryAcquire(attemptId)) {
            throw new FeedbackGenerationInProgressException(attemptId);
        }

        try {
            AttemptView attempt = getAttempt(attemptId);

            if (attempt.turns().isEmpty()) {
                throw new AttemptHasNoTurnsException(attemptId);
            }

            if (attempt.status() == AttemptStatus.SUBMITTED) {
                return attempt.feedback();
            }

            String feedback = feedbackGenerator.generate(ProblemView.from(getProblem(attempt.problemId())), attempt);

            return attemptWriter.submit(attemptId, feedback);
        } finally {
            feedbackGenerationGuard.release(attemptId);
        }
    }

    private Problem getProblem(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));
    }
}
