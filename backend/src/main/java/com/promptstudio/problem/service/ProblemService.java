package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemView;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;

    public ProblemService(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    @Transactional(readOnly = true)
    public List<ProblemView> getProblems() {
        List<ProblemView> views = new ArrayList<>();

        for (Problem problem : problemRepository.findAll()) {
            views.add(ProblemView.from(problem));
        }

        return views;
    }

    @Transactional(readOnly = true)
    public ProblemView getProblem(Long id) {
        return ProblemView.from(problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id)));
    }
}
