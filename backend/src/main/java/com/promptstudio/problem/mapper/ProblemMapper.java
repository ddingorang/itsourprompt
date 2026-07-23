package com.promptstudio.problem.mapper;

import com.promptstudio.problem.dto.response.ProblemListResponse;
import com.promptstudio.problem.dto.response.ProblemDetailResponse;
import com.promptstudio.problem.dto.response.RunResponse;
import com.promptstudio.problem.entity.Problem;
import com.promptstudio.problem.entity.ProblemFile;

import java.util.List;

public interface ProblemMapper {

    ProblemListResponse toListResponse(List<Problem> problems);

    ProblemDetailResponse toDetailResponse(Problem problem);

    List<RunResponse.RunFileResponse> toRunFileResponses(List<ProblemFile> files);
}
