package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.exception.AttemptHasNoTurnsException;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.port.CodeGenerator;
import com.promptstudio.attempt.port.FeedbackGenerator;
import com.promptstudio.attempt.repository.AttemptRepository;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import org.springframework.stereotype.Service;

@Service
public class AttemptService {

    private final ProblemRepository problemRepository;
    private final AttemptRepository attemptRepository;
    private final CodeGenerator codeGenerator;
    private final FeedbackGenerator feedbackGenerator;

    public AttemptService(
            ProblemRepository problemRepository,
            AttemptRepository attemptRepository,
            CodeGenerator codeGenerator,
            FeedbackGenerator feedbackGenerator
    ) {
        this.problemRepository = problemRepository;
        this.attemptRepository = attemptRepository;
        this.codeGenerator = codeGenerator;
        this.feedbackGenerator = feedbackGenerator;
    }

    public Attempt startAttempt(Long problemId) {
        return attemptRepository.save(Attempt.start(getProblem(problemId)));
    }

    public Attempt getAttempt(Long attemptId) {
        return attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    public Attempt addTurn(Long attemptId, String userPrompt) {
        Attempt attempt = getAttempt(attemptId);
        GeneratedCode generated = codeGenerator.generate(attempt, userPrompt);

        return attemptRepository.save(attempt.applyTurn(userPrompt, generated));
    }

    public String generateFeedback(Long attemptId) {
        Attempt attempt = getAttempt(attemptId);

        if (attempt.turns().isEmpty()) {
            throw new AttemptHasNoTurnsException(attemptId);
        }

        return feedbackGenerator.generate(getProblem(attempt.problemId()), attempt);
    }

    private Problem getProblem(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));
    }
}
