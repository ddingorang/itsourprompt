package com.promptstudio.problem.controller;

import com.promptstudio.global.exception.ApiErrorResponse;
import com.promptstudio.problem.dto.response.ProblemDetailResponse;
import com.promptstudio.problem.dto.response.ProblemListResponse;
import com.promptstudio.problem.dto.request.RunRequest;
import com.promptstudio.problem.dto.response.RunResponse;
import com.promptstudio.problem.dto.request.SubmitRequest;
import com.promptstudio.problem.dto.response.SubmitResponse;
import com.promptstudio.problem.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/problems")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(name = "Problems", description = "풀이 문제 조회 API")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    @Operation(
            summary = "문제 목록 조회",
            description = "랜딩 페이지에서 선택할 수 있는 문제의 ID와 제목만 반환합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "문제 목록 조회 성공",
            content = @Content(
                    schema = @Schema(implementation = ProblemListResponse.class),
                    examples = @ExampleObject(
                            name = "문제 목록 예시",
                            value = """
                                    {
                                      \"problems\": [
                                        { \"id\": 1, \"title\": \"Hello World 출력\" },
                                        { \"id\": 2, \"title\": \"SSAFY 출력\" },
                                        { \"id\": 3, \"title\": \"환영 메시지 출력\" }
                                      ]
                                    }
                                    """
                    )
            )
    )
    public ProblemListResponse getProblems() {
        return problemService.getProblems();
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "문제 상세 조회",
            description = "문제 명세와 스켈레톤 파일 전체를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = ProblemDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "문제를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ProblemDetailResponse getProblem(@PathVariable("id") Long id) {
        return problemService.getProblem(id);
    }

    @PostMapping("/{id}/run")
    @Operation(
            summary = "AI 코드 실행",
            description = "문제 스켈레톤과 프롬프트를 NVIDIA AI에 전달해 최종 파일 전체를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "AI 코드 실행 성공",
                    content = @Content(schema = @Schema(implementation = RunResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "문제를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "AI 제공자 호출 실패",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "AI 코드 실행 시간 초과",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public RunResponse runProblem(
            @PathVariable("id") Long id,
            @Valid @RequestBody RunRequest request
    ) {
        return problemService.runProblem(id, request);
    }

    @PostMapping("/{id}/submit")
    @Operation(
            summary = "프롬프트 피드백 제출",
            description = "문제 명세와 직전 실행 요약을 바탕으로 프롬프트 피드백을 생성합니다. 생성된 코드 내용은 받지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "프롬프트 피드백 생성 성공",
                    content = @Content(schema = @Schema(implementation = SubmitResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 제출 요청",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "문제를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "AI 제공자 호출 실패",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "피드백 생성 시간 초과",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public SubmitResponse submitProblem(
            @PathVariable("id") Long id,
            @Valid @RequestBody SubmitRequest request
    ) {
        return problemService.submitProblem(id, request);
    }
}
