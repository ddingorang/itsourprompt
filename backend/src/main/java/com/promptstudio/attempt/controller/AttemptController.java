package com.promptstudio.attempt.controller;

import com.promptstudio.attempt.controller.request.CreateAttemptRequest;
import com.promptstudio.attempt.controller.request.TurnRequest;
import com.promptstudio.attempt.controller.response.AttemptResponse;
import com.promptstudio.attempt.controller.response.FeedbackResponse;
import com.promptstudio.attempt.service.AttemptService;
import com.promptstudio.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attempts")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(name = "Attempts", description = "문제 풀이 어템프트 API")
public class AttemptController {

    private final AttemptService attemptService;
    private final AttemptWebMapper attemptWebMapper;

    public AttemptController(AttemptService attemptService, AttemptWebMapper attemptWebMapper) {
        this.attemptService = attemptService;
        this.attemptWebMapper = attemptWebMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "어템프트 시작",
            description = "문제 스켈레톤 파일로 초기화된 새 어템프트를 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "어템프트 생성 성공",
                    content = @Content(schema = @Schema(implementation = AttemptResponse.class))
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
            )
    })
    public AttemptResponse createAttempt(@Valid @RequestBody CreateAttemptRequest request) {
        return attemptWebMapper.toAttemptResponse(attemptService.startAttempt(request.problemId()));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "어템프트 조회",
            description = "어템프트의 현재 파일 전체와 턴 기록을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "어템프트 조회 성공",
                    content = @Content(schema = @Schema(implementation = AttemptResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "어템프트를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public AttemptResponse getAttempt(@PathVariable("id") Long id) {
        return attemptWebMapper.toAttemptResponse(attemptService.getAttempt(id));
    }

    @PostMapping("/{id}/turns")
    @Operation(
            summary = "턴 추가",
            description = "이전 대화 이력과 현재 파일을 OpenAI에 전달해 코드를 갱신하고, 갱신된 어템프트 상태를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "턴 추가 성공",
                    content = @Content(schema = @Schema(implementation = AttemptResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "어템프트를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "AI 제공자 호출 실패",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "AI 코드 생성 시간 초과",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public AttemptResponse addTurn(
            @PathVariable("id") Long id,
            @Valid @RequestBody TurnRequest request
    ) {
        return attemptWebMapper.toAttemptResponse(attemptService.addTurn(id, request.prompt()));
    }

    @PostMapping("/{id}/feedback")
    @Operation(
            summary = "프롬프트 피드백 생성",
            description = "문제 명세와 어템프트의 전체 턴 기록을 바탕으로 프롬프트 피드백을 생성합니다. 코드 내용은 전달하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "프롬프트 피드백 생성 성공",
                    content = @Content(schema = @Schema(implementation = FeedbackResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "턴이 없는 어템프트",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "어템프트를 찾을 수 없음",
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
    public FeedbackResponse generateFeedback(@PathVariable("id") Long id) {
        return new FeedbackResponse(attemptService.generateFeedback(id));
    }
}
