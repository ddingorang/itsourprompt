package com.promptstudio.problem.mapper;

import com.promptstudio.problem.dto.response.ProblemListResponse;
import com.promptstudio.problem.dto.response.ProblemDetailResponse;
import com.promptstudio.problem.entity.Problem;

import java.util.List;

public interface ProblemMapper {

    ProblemListResponse toListResponse(List<Problem> problems);

    ProblemDetailResponse toDetailResponse(Problem problem);
}
