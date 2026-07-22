package com.promptstudio.problem.service;

import com.promptstudio.problem.dto.response.ProblemListResponse;
import com.promptstudio.problem.dto.response.ProblemDetailResponse;

public interface ProblemService {

    ProblemListResponse getProblems();

    ProblemDetailResponse getProblem(Long id);
}
