package com.promptstudio.problem.controller;

import com.promptstudio.problem.controller.response.ProblemDetailResponse;
import com.promptstudio.problem.controller.response.ProblemListResponse;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.ProblemView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProblemWebMapper {

    public ProblemListResponse toListResponse(List<ProblemView> problems) {
        List<ProblemListResponse.ProblemSummaryResponse> summaries = new ArrayList<>();

        for (ProblemView problem : problems) {
            summaries.add(new ProblemListResponse.ProblemSummaryResponse(
                    problem.id(),
                    problem.title()
            ));
        }

        return new ProblemListResponse(summaries);
    }

    public ProblemDetailResponse toDetailResponse(ProblemView problem) {
        List<ProblemDetailResponse.ProblemFileResponse> files = new ArrayList<>();

        for (ProblemFile file : problem.files()) {
            files.add(new ProblemDetailResponse.ProblemFileResponse(
                    file.path(),
                    file.content()
            ));
        }

        return new ProblemDetailResponse(
                problem.id(),
                problem.title(),
                problem.specMd(),
                files
        );
    }
}
