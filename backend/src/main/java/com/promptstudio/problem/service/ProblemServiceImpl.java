package com.promptstudio.problem.service;

import com.promptstudio.problem.dto.response.ProblemListResponse;
import com.promptstudio.problem.dto.response.ProblemDetailResponse;
import com.promptstudio.problem.entity.Problem;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.mapper.ProblemMapper;
import com.promptstudio.problem.repository.ProblemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProblemServiceImpl implements ProblemService {

    private final ProblemRepository problemRepository;
    private final ProblemMapper problemMapper;

    public ProblemServiceImpl(ProblemRepository problemRepository, ProblemMapper problemMapper) {
        this.problemRepository = problemRepository;
        this.problemMapper = problemMapper;
    }

    @Override
    public ProblemListResponse getProblems() {
        List<Problem> problems = problemRepository.findAll();
        return problemMapper.toListResponse(problems);
    }

    @Override
    public ProblemDetailResponse getProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        return problemMapper.toDetailResponse(problem);
    }
}
