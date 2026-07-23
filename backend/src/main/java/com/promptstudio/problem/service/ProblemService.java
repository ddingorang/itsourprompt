package com.promptstudio.problem.service;

import com.promptstudio.problem.dto.response.ProblemListResponse;
import com.promptstudio.problem.dto.response.ProblemDetailResponse;
import com.promptstudio.problem.dto.request.RunRequest;
import com.promptstudio.problem.dto.request.SubmitRequest;
import com.promptstudio.problem.dto.response.RunResponse;
import com.promptstudio.problem.dto.response.SubmitResponse;

public interface ProblemService {

    ProblemListResponse getProblems();

    ProblemDetailResponse getProblem(Long id);

    RunResponse runProblem(Long id, RunRequest request);

    SubmitResponse submitProblem(Long id, SubmitRequest request);
}
