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

        // 저장소에서 사라진 문제는 목록에서 감춘다. 이미 시작한 어템프트를 위해 단건 조회는 계속 열어 둔다.
        for (Problem problem : problemRepository.findAll()) {
            if (problem.active()) {
                views.add(ProblemView.from(problem));
            }
        }

        return views;
    }

    @Transactional(readOnly = true)
    public ProblemView getProblem(Long id) {
        return ProblemView.from(problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id)));
    }
}
