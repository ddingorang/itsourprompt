package com.promptstudio.problem.mapper;

import com.promptstudio.problem.dto.response.ProblemListResponse;
import com.promptstudio.problem.entity.Problem;
import com.promptstudio.problem.entity.ProblemFile;
import com.promptstudio.problem.dto.response.ProblemDetailResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProblemMapperImpl implements ProblemMapper {

    @Override
    public ProblemListResponse toListResponse(List<Problem> problems) {
        List<ProblemListResponse.ProblemSummaryResponse> summaries = new ArrayList<>();

        for (Problem problem : problems) {
            ProblemListResponse.ProblemSummaryResponse summary =
                    new ProblemListResponse.ProblemSummaryResponse(
                            problem.id(),
                            problem.title()
                    );

            summaries.add(summary);
        }

        return new ProblemListResponse(summaries);
    }

    @Override
    public ProblemDetailResponse toDetailResponse(Problem problem) {
        List<ProblemDetailResponse.ProblemFileResponse> files = new ArrayList<>();

        for (ProblemFile file : problem.files()) {
            ProblemDetailResponse.ProblemFileResponse responseFile =
                    new ProblemDetailResponse.ProblemFileResponse(
                            file.path(),
                            file.content()
                    );

            files.add(responseFile);
        }

        return new ProblemDetailResponse(
                problem.id(),
                problem.title(),
                problem.specMd(),
                files
        );
    }
}
