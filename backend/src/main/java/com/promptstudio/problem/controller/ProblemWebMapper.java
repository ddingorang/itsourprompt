package com.promptstudio.problem.controller;

import com.promptstudio.problem.controller.request.SubmitRequest;
import com.promptstudio.problem.controller.response.ChangedFileResponse;
import com.promptstudio.problem.controller.response.ProblemDetailResponse;
import com.promptstudio.problem.controller.response.ProblemListResponse;
import com.promptstudio.problem.controller.response.RunResponse;
import com.promptstudio.problem.domain.FileChange;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.Submission;
import com.promptstudio.problem.service.RunResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProblemWebMapper {

    public ProblemListResponse toListResponse(List<Problem> problems) {
        List<ProblemListResponse.ProblemSummaryResponse> summaries = new ArrayList<>();

        for (Problem problem : problems) {
            summaries.add(new ProblemListResponse.ProblemSummaryResponse(
                    problem.id(),
                    problem.title()
            ));
        }

        return new ProblemListResponse(summaries);
    }

    public ProblemDetailResponse toDetailResponse(Problem problem) {
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

    public RunResponse toRunResponse(RunResult result) {
        List<RunResponse.RunFileResponse> files = new ArrayList<>();

        for (ProblemFile file : result.files()) {
            files.add(new RunResponse.RunFileResponse(file.path(), file.content()));
        }

        List<ChangedFileResponse> changedFiles = new ArrayList<>();

        for (FileChange change : result.changes()) {
            changedFiles.add(new ChangedFileResponse(change.path(), change.type()));
        }

        return new RunResponse(files, changedFiles, result.summary());
    }

    public Submission toSubmission(SubmitRequest request) {
        List<FileChange> changes = new ArrayList<>();

        for (SubmitRequest.ChangedFile changedFile : request.changedFiles()) {
            changes.add(new FileChange(changedFile.path(), changedFile.changeType()));
        }

        return new Submission(request.prompt(), request.aiResponse(), changes);
    }
}
