package com.promptstudio.problem.service;

import com.promptstudio.ai.dto.AiCodeResult;
import com.promptstudio.ai.service.AiCodeService;
import com.promptstudio.ai.service.AiFeedbackService;
import com.promptstudio.problem.dto.request.RunRequest;
import com.promptstudio.problem.dto.request.SubmitRequest;
import com.promptstudio.problem.dto.response.ChangedFileResponse;
import com.promptstudio.problem.dto.response.ProblemListResponse;
import com.promptstudio.problem.dto.response.ProblemDetailResponse;
import com.promptstudio.problem.dto.response.RunResponse;
import com.promptstudio.problem.dto.response.SubmitResponse;
import com.promptstudio.problem.entity.Problem;
import com.promptstudio.problem.entity.ProblemFile;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.mapper.ProblemMapper;
import com.promptstudio.problem.repository.ProblemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class ProblemServiceImpl implements ProblemService {

    private final ProblemRepository problemRepository;
    private final ProblemMapper problemMapper;
    private final AiCodeService aiCodeService;
    private final AiFeedbackService aiFeedbackService;

    public ProblemServiceImpl(
            ProblemRepository problemRepository,
            ProblemMapper problemMapper,
            AiCodeService aiCodeService,
            AiFeedbackService aiFeedbackService
    ) {
        this.problemRepository = problemRepository;
        this.problemMapper = problemMapper;
        this.aiCodeService = aiCodeService;
        this.aiFeedbackService = aiFeedbackService;
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

    @Override
    public RunResponse runProblem(Long id, RunRequest request) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        AiCodeResult aiResult = aiCodeService.generateCode(problem, request.prompt());
        List<ChangedFileResponse> changedFiles = findChangedFiles(problem.files(), aiResult.files());

        return new RunResponse(
                problemMapper.toRunFileResponses(aiResult.files()),
                changedFiles,
                aiResult.aiResponse()
        );
    }

    @Override
    public SubmitResponse submitProblem(Long id, SubmitRequest request) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        String feedback = aiFeedbackService.generateFeedback(problem, request);
        return new SubmitResponse(feedback);
    }

    private List<ChangedFileResponse> findChangedFiles(
            List<ProblemFile> originalFiles,
            List<ProblemFile> finalFiles
    ) {
        Map<String, String> originalContents = toContentByPath(originalFiles);
        Map<String, String> finalContents = toContentByPath(finalFiles);
        TreeSet<String> paths = new TreeSet<>();
        paths.addAll(originalContents.keySet());
        paths.addAll(finalContents.keySet());

        List<ChangedFileResponse> changedFiles = new ArrayList<>();

        for (String path : paths) {
            boolean existedBefore = originalContents.containsKey(path);
            boolean existsNow = finalContents.containsKey(path);

            if (!existedBefore) {
                changedFiles.add(new ChangedFileResponse(path, ChangedFileResponse.ChangeType.ADDED));
            } else if (!existsNow) {
                changedFiles.add(new ChangedFileResponse(path, ChangedFileResponse.ChangeType.DELETED));
            } else if (!originalContents.get(path).equals(finalContents.get(path))) {
                changedFiles.add(new ChangedFileResponse(path, ChangedFileResponse.ChangeType.MODIFIED));
            }
        }

        return changedFiles;
    }

    private Map<String, String> toContentByPath(List<ProblemFile> files) {
        Map<String, String> contents = new HashMap<>();

        for (ProblemFile file : files) {
            contents.put(file.path(), file.content());
        }

        return contents;
    }
}
