package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.attempt.exception.AttemptHasNoTurnsException;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.port.CodeGenerator;
import com.promptstudio.attempt.port.FeedbackGenerator;
import com.promptstudio.attempt.repository.AttemptQueryRepository;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemView;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import org.springframework.stereotype.Service;

@Service
public class AttemptService {

    private final ProblemRepository problemRepository;
    private final AttemptQueryRepository attemptQueryRepository;
    private final AttemptWriter attemptWriter;
    private final CodeGenerator codeGenerator;
    private final FeedbackGenerator feedbackGenerator;

    public AttemptService(
            ProblemRepository problemRepository,
            AttemptQueryRepository attemptQueryRepository,
            AttemptWriter attemptWriter,
            CodeGenerator codeGenerator,
            FeedbackGenerator feedbackGenerator
    ) {
        this.problemRepository = problemRepository;
        this.attemptQueryRepository = attemptQueryRepository;
        this.attemptWriter = attemptWriter;
        this.codeGenerator = codeGenerator;
        this.feedbackGenerator = feedbackGenerator;
    }

    public AttemptView startAttempt(Long problemId) {
        return attemptWriter.start(getProblem(problemId));
    }

    public AttemptView getAttempt(Long attemptId) {
        return attemptQueryRepository.findById(attemptId)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    public AttemptView addTurn(Long attemptId, String userPrompt) {
        AttemptView attempt = getAttempt(attemptId);
        GeneratedCode generated = codeGenerator.generate(attempt, userPrompt);

        return attemptWriter.appendTurn(attemptId, userPrompt, generated);
    }

    public String generateFeedback(Long attemptId) {
        AttemptView attempt = getAttempt(attemptId);

        if (attempt.turns().isEmpty()) {
            throw new AttemptHasNoTurnsException(attemptId);
        }

        return feedbackGenerator.generate(ProblemView.from(getProblem(attempt.problemId())), attempt);
    }

    private Problem getProblem(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));
    }
}
