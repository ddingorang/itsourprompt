package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.FileChange;
import com.promptstudio.problem.domain.FileChanges;
import com.promptstudio.problem.domain.GeneratedCode;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.Submission;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.problem.port.CodeGenerator;
import com.promptstudio.problem.port.FeedbackGenerator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final CodeGenerator codeGenerator;
    private final FeedbackGenerator feedbackGenerator;

    public ProblemService(
            ProblemRepository problemRepository,
            CodeGenerator codeGenerator,
            FeedbackGenerator feedbackGenerator
    ) {
        this.problemRepository = problemRepository;
        this.codeGenerator = codeGenerator;
        this.feedbackGenerator = feedbackGenerator;
    }

    public List<Problem> getProblems() {
        return problemRepository.findAll();
    }

    public Problem getProblem(Long id) {
        return problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));
    }

    public RunResult run(Long id, String userPrompt) {
        Problem problem = getProblem(id);
        GeneratedCode generated = codeGenerator.generate(problem, userPrompt);
        List<FileChange> changes = FileChanges.diff(problem.files(), generated.files());

        return new RunResult(generated.files(), changes, generated.summary());
    }

    public String submit(Long id, Submission submission) {
        Problem problem = getProblem(id);
        return feedbackGenerator.generate(problem, submission);
    }
}
