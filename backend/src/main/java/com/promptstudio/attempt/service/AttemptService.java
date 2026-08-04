package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.domain.LlmCallPurpose;
import com.promptstudio.attempt.domain.PromptScopeDecision;
import com.promptstudio.attempt.exception.AttemptAlreadySubmittedException;
import com.promptstudio.attempt.exception.AttemptHasNoTurnsException;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.exception.CodeGenerationInProgressException;
import com.promptstudio.attempt.exception.FeedbackGenerationInProgressException;
import com.promptstudio.attempt.exception.FeedbackNotFoundException;
import com.promptstudio.attempt.exception.PromptScopeRejectedException;
import com.promptstudio.problem.exception.InactiveProblemException;
import com.promptstudio.attempt.port.CodeGenerator;
import com.promptstudio.attempt.port.FeedbackGenerator;
import com.promptstudio.attempt.port.LlmUsageCarrier;
import com.promptstudio.attempt.port.PromptScopeValidator;
import com.promptstudio.attempt.repository.AttemptQueryRepository;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemView;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@Service
public class AttemptService {

    private static final Logger log = LoggerFactory.getLogger(AttemptService.class);

    private final ProblemRepository problemRepository;
    private final AttemptQueryRepository attemptQueryRepository;
    private final AttemptWriter attemptWriter;
    private final IdempotencyGuard idempotencyGuard;
    private final FeedbackGenerationGuard feedbackGenerationGuard;
    private final CodeGenerationGuard codeGenerationGuard;
    private final CodeGenerator codeGenerator;
    private final FeedbackGenerator feedbackGenerator;
    private final PromptScopeValidator promptScopeValidator;

    public AttemptService(
            ProblemRepository problemRepository,
            AttemptQueryRepository attemptQueryRepository,
            AttemptWriter attemptWriter,
            IdempotencyGuard idempotencyGuard,
            FeedbackGenerationGuard feedbackGenerationGuard,
            CodeGenerationGuard codeGenerationGuard,
            CodeGenerator codeGenerator,
            FeedbackGenerator feedbackGenerator,
            PromptScopeValidator promptScopeValidator
    ) {
        this.problemRepository = problemRepository;
        this.attemptQueryRepository = attemptQueryRepository;
        this.attemptWriter = attemptWriter;
        this.idempotencyGuard = idempotencyGuard;
        this.feedbackGenerationGuard = feedbackGenerationGuard;
        this.codeGenerationGuard = codeGenerationGuard;
        this.codeGenerator = codeGenerator;
        this.feedbackGenerator = feedbackGenerator;
        this.promptScopeValidator = promptScopeValidator;
    }

    public AttemptView startAttempt(Long problemId, Long userId, String idempotencyKey) {
        return startAttempt(problemId, AttemptOwner.user(userId), idempotencyKey);
    }

    public AttemptView startAttempt(Long problemId, AttemptOwner owner, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);

        if (key == null) {
            return attemptWriter.start(getActiveProblem(problemId), owner, null);
        }

        String scopedKey = IdempotencyGuard.scopedKey(owner, key);
        return withIdempotency(owner, scopedKey, () -> attemptWriter.start(getActiveProblem(problemId), owner, scopedKey));
    }

    public AttemptView getAttempt(Long attemptId, Long userId) {
        return getAttempt(attemptId, AttemptOwner.user(userId));
    }

    public AttemptView getAttempt(Long attemptId, AttemptOwner owner) {
        return findAttempt(attemptId, owner)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    public AttemptView getFeedback(Long attemptId, Long userId) {
        return getFeedback(attemptId, AttemptOwner.user(userId));
    }

    public AttemptView getFeedback(Long attemptId, AttemptOwner owner) {
        AttemptView attempt = getAttempt(attemptId, owner);

        if (attempt.status() != AttemptStatus.SUBMITTED) {
            throw new FeedbackNotFoundException(attemptId);
        }

        return attempt;
    }

    public AttemptView addTurn(Long attemptId, Long userId, String userPrompt) {
        return addTurn(attemptId, AttemptOwner.user(userId), userPrompt, null);
    }

    public AttemptView addTurn(Long attemptId, Long userId, String userPrompt, String idempotencyKey) {
        return addTurn(attemptId, AttemptOwner.user(userId), userPrompt, idempotencyKey);
    }

    public AttemptView addTurn(Long attemptId, AttemptOwner owner, String userPrompt, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);

        if (key == null) {
            return generateTurn(attemptId, owner, userPrompt, null);
        }

        String scopedKey = IdempotencyGuard.scopedKey(owner, key);
        return withIdempotency(owner, scopedKey, () -> generateTurn(attemptId, owner, userPrompt, scopedKey));
    }

    private AttemptView generateTurn(Long attemptId, AttemptOwner owner, String userPrompt, String idempotencyKey) {
        if (!codeGenerationGuard.tryAcquire(attemptId)) {
            log.warn("AI code generation rejected because another request is in progress | attemptId={}", attemptId);
            throw new CodeGenerationInProgressException(attemptId);
        }

        long startedAt = System.nanoTime();
        log.info("AI code generation started | attemptId={}", attemptId);

        try {
            AttemptView result = generateTurnWhileGuarded(attemptId, owner, userPrompt, idempotencyKey);
            log.info("AI code generation completed | attemptId={} elapsedMs={}", attemptId, elapsedMillis(startedAt));
            return result;
        } catch (RuntimeException exception) {
            log.warn("AI code generation failed | attemptId={} elapsedMs={} exceptionType={}",
                    attemptId, elapsedMillis(startedAt), exception.getClass().getSimpleName(), exception);
            throw exception;
        } finally {
            codeGenerationGuard.release(attemptId);
        }
    }

    private AttemptView generateTurnWhileGuarded(Long attemptId, AttemptOwner owner, String userPrompt, String idempotencyKey) {
        if (feedbackGenerationGuard.isGenerating(attemptId)) {
            throw new FeedbackGenerationInProgressException(attemptId);
        }

        AttemptView attempt = getAttempt(attemptId, owner);

        if (attempt.status() == AttemptStatus.SUBMITTED) {
            throw new AttemptAlreadySubmittedException(attemptId);
        }

        ProblemView problem = ProblemView.from(getProblem(attempt.problemId()));
        PromptScopeDecision scopeDecision = promptScopeValidator.validate(problem, userPrompt);

        if (scopeDecision.isRejected()) {
            throw new PromptScopeRejectedException(scopeRejectionMessage(scopeDecision));
        }

        GeneratedCode generated;

        try {
            generated = codeGenerator.generate(
                    problem, attempt, userPrompt);
        } catch (RuntimeException exception) {
            recordFailedCalls(attemptId, LlmCallPurpose.CODE, exception, userPrompt);

            throw exception;
        }

        attemptWriter.appendTurn(attemptId, owner, userPrompt, generated, idempotencyKey);

        // 사용량은 호출 행에서 파생하므로 커밋 후 조회 seam으로 다시 읽는다 — 멱등 replay 경로와 같은 조립이다.
        return getAttempt(attemptId, owner);
    }

    /**
     * 실패한 호출이 실어 온 사용량과 입력을 기록한다. 기록에 실패해도 원 예외를 가리지 않는다 —
     * 클라이언트가 받는 502/504는 그대로 두고 기록 실패만 덧붙인다.
     */
    private void recordFailedCalls(
            Long attemptId,
            LlmCallPurpose purpose,
            RuntimeException exception,
            String userPrompt
    ) {
        if (!(exception instanceof LlmUsageCarrier carrier)) {
            return;
        }

        try {
            attemptWriter.recordFailure(attemptId, purpose, carrier.llmCalls(), carrier.errorType(), userPrompt);
        } catch (RuntimeException flushFailure) {
            exception.addSuppressed(flushFailure);
        }
    }

    private AttemptView withIdempotency(AttemptOwner owner, String key, Supplier<AttemptView> action) {
        return switch (idempotencyGuard.reserve(key, owner)) {
            case IdempotencyGuard.Reservation.Replay(Long attemptId) -> getAttempt(attemptId, owner);
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

    public AttemptView submit(Long attemptId, Long userId) {
        return submit(attemptId, AttemptOwner.user(userId));
    }

    public AttemptView submit(Long attemptId, AttemptOwner owner) {
        if (codeGenerationGuard.isGenerating(attemptId)) {
            log.warn("Feedback generation rejected because AI code generation is in progress | attemptId={}", attemptId);
            throw new CodeGenerationInProgressException(attemptId);
        }

        if (!feedbackGenerationGuard.tryAcquire(attemptId)) {
            throw new FeedbackGenerationInProgressException(attemptId);
        }

        try {
            AttemptView attempt = getAttempt(attemptId, owner);

            if (attempt.turns().isEmpty()) {
                throw new AttemptHasNoTurnsException(attemptId);
            }

            if (attempt.status() == AttemptStatus.SUBMITTED) {
                return attempt;
            }

            AttemptFeedback feedback;

            try {
                feedback = feedbackGenerator.generate(ProblemView.from(getProblem(attempt.problemId())), attempt);
            } catch (RuntimeException exception) {
                // 피드백 프롬프트는 사용자 입력이 아니라 어댑터 산출물이고, 제출이 되지 않아
                // 그 입력(problem·turns·baseFiles)은 어템프트에 그대로 남는다.
                recordFailedCalls(attemptId, LlmCallPurpose.FEEDBACK, exception, null);

                throw exception;
            }

            return attemptWriter.submit(attemptId, owner, feedback);
        } finally {
            feedbackGenerationGuard.release(attemptId);
        }
    }

    private Problem getProblem(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));
    }

    private String scopeRejectionMessage(PromptScopeDecision decision) {
        if (decision.status() == PromptScopeDecision.Status.OUT_OF_SCOPE) {
            return "현재 요청은 이 문제의 범위와 맞지 않아 실행할 수 없습니다.\n" + decision.message();
        }

        return "요청이 구체적이지 않아 실행할 수 없습니다.\n" + decision.message();
    }

    /**
     * 이미 시작한 어템프트는 계속 풀 수 있지만, 비활성 문제로 새로 시작할 수는 없다.
     */
    private Problem getActiveProblem(Long problemId) {
        Problem problem = getProblem(problemId);

        if (!problem.active()) {
            throw new InactiveProblemException(problemId);
        }

        return problem;
    }

    private java.util.Optional<AttemptView> findAttempt(Long attemptId, AttemptOwner owner) {
        if (owner.isUser()) {
            return attemptQueryRepository.findByIdAndUserId(attemptId, owner.userId());
        }
        return attemptQueryRepository.findByIdAndGuestSessionId(attemptId, owner.guestSessionId());
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
