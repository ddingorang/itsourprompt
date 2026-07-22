package com.promptstudio.problem.controller;

import com.promptstudio.global.exception.ApiErrorResponse;
import com.promptstudio.problem.dto.response.ProblemDetailResponse;
import com.promptstudio.problem.dto.response.ProblemListResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
