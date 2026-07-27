package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;

    public ProblemService(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    public List<Problem> getProblems() {
        return problemRepository.findAll();
    }

    public Problem getProblem(Long id) {
        return problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));
    }
}
