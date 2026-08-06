package com.promptstudio.attempt.controller;

import com.promptstudio.attempt.controller.request.CreateAttemptRequest;
import com.promptstudio.attempt.controller.request.TurnRequest;
import com.promptstudio.attempt.controller.response.AttemptResponse;
import com.promptstudio.attempt.controller.response.CodeRunListResponse;
import com.promptstudio.attempt.controller.response.CodeRunResponse;
import com.promptstudio.attempt.controller.response.FeedbackResponse;
import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.service.AttemptService;
import com.promptstudio.attempt.service.CodeRunService;
import com.promptstudio.global.exception.ApiErrorResponse;
import com.promptstudio.global.security.AppUserDetails;
import com.promptstudio.guest.AttemptOwnerResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/attempts")
@Tag(name = "Attempts", description = "문제 풀이 어템프트 API")
public class AttemptController {

    private final AttemptService attemptService;
    private final CodeRunService codeRunService;
    private final AttemptWebMapper attemptWebMapper;
    private final AttemptOwnerResolver attemptOwnerResolver;

    public AttemptController(
            AttemptService attemptService,
            CodeRunService codeRunService,
            AttemptWebMapper attemptWebMapper,
            AttemptOwnerResolver attemptOwnerResolver
    ) {
        this.attemptService = attemptService;
        this.codeRunService = codeRunService;
        this.attemptWebMapper = attemptWebMapper;
        this.attemptOwnerResolver = attemptOwnerResolver;
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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "같은 Idempotency-Key의 요청이 처리 중",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public AttemptResponse createAttempt(
            @AuthenticationPrincipal AppUserDetails principal,
            HttpServletRequest httpRequest,
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    description = "중복 요청 방지 키(선택). 같은 키로 다시 요청하면 새로 만들지 않고 기존 어템프트를 반환합니다."
            )
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateAttemptRequest request
    ) {
        AttemptOwner requester = owner(principal, httpRequest);

        return attemptWebMapper.toAttemptResponse(
                attemptService.startAttempt(request.problemId(), requester, idempotencyKey), requester);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "어템프트 조회",
            description = "어템프트의 현재 파일 전체와 턴 기록을 반환합니다. "
                    + "제출 완료된 어템프트는 누구나 조회할 수 있고, 진행 중이면 소유자만 조회할 수 있습니다."
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
    public AttemptResponse getAttempt(
            @AuthenticationPrincipal AppUserDetails principal,
            HttpServletRequest httpRequest,
            @PathVariable("id") Long id
    ) {
        AttemptOwner requester = owner(principal, httpRequest);

        return attemptWebMapper.toAttemptResponse(attemptService.readAttempt(id, requester), requester);
    }

    @GetMapping("/{id}/feedback")
    @Operation(
            summary = "피드백 조회",
            description = "제출 시 생성해 저장한 프롬프트 피드백을 턴별 피드백과 전체 피드백으로 반환합니다. "
                    + "제출 전에는 조회할 수 없고, 제출 완료된 어템프트의 피드백은 누구나 조회할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "피드백 조회 성공",
                    content = @Content(schema = @Schema(implementation = FeedbackResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "어템프트를 찾을 수 없거나, 아직 제출하지 않아 피드백이 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public FeedbackResponse getFeedback(
            @AuthenticationPrincipal AppUserDetails principal,
            HttpServletRequest httpRequest,
            @PathVariable("id") Long id
    ) {
        return attemptWebMapper.toFeedbackResponse(attemptService.readFeedback(id, owner(principal, httpRequest)));
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
                    responseCode = "409",
                    description = "같은 Idempotency-Key의 요청 처리 중, 이미 제출된 어템프트, 피드백 생성 진행 중 또는 다른 창의 AI 코드 생성 진행 중",
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
            @AuthenticationPrincipal AppUserDetails principal,
            HttpServletRequest httpRequest,
            @PathVariable("id") Long id,
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    description = "중복 요청 방지 키(선택). 같은 키로 다시 요청하면 AI를 재호출하지 않고 기존 결과를 반환합니다."
            )
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TurnRequest request
    ) {
        AttemptOwner requester = owner(principal, httpRequest);

        return attemptWebMapper.toAttemptResponse(
                attemptService.addTurn(id, requester, request.prompt(), idempotencyKey), requester);
    }

    @PostMapping("/{id}/submit")
    @Operation(
            summary = "어템프트 제출",
            description = "문제 명세와 어템프트의 전체 턴 기록을 바탕으로 턴별 프롬프트 피드백과 세션 전체 피드백을 생성해 "
                    + "저장하고 어템프트를 종료합니다. 이미 제출된 어템프트를 다시 제출하면 AI를 재호출하지 않고 "
                    + "저장된 피드백을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "제출 성공",
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
                    responseCode = "409",
                    description = "피드백 생성이 진행 중",
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
    public FeedbackResponse submit(
            @AuthenticationPrincipal AppUserDetails principal,
            HttpServletRequest httpRequest,
            @PathVariable("id") Long id
    ) {
        return attemptWebMapper.toFeedbackResponse(attemptService.submit(id, owner(principal, httpRequest)));
    }

    @PostMapping("/{id}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "코드 빌드/실행 요청 (마지막 턴)",
            description = "마지막 턴의 파일 전체를 빌드/실행 워커에 큐로 넘기고 즉시 실행 ID를 반환합니다. "
                    + "턴이 없으면 시작 스켈레톤을 실행합니다. 결과는 조회 API로 폴링해서 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "실행 요청 접수",
                    content = @Content(schema = @Schema(implementation = CodeRunResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "어템프트를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "해당 어템프트의 코드 실행이 이미 진행 중",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public CodeRunResponse requestRun(
            @AuthenticationPrincipal AppUserDetails principal,
            HttpServletRequest httpRequest,
            @PathVariable("id") Long id
    ) {
        return attemptWebMapper.toCodeRunResponse(codeRunService.requestRun(id, owner(principal, httpRequest)));
    }

    @PostMapping("/{id}/turns/{ordinal}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "코드 빌드/실행 요청 (턴 지정)",
            description = "지정한 턴 시점의 파일로 빌드/실행합니다. 턴은 불변이므로 과거 턴을 다시 실행하면 "
                    + "그때의 코드가 그대로 실행됩니다. 어느 프롬프트까지 테스트를 통과했는지 확인할 때 씁니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "실행 요청 접수",
                    content = @Content(schema = @Schema(implementation = CodeRunResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "어템프트 또는 턴을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "해당 어템프트의 코드 실행이 이미 진행 중",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public CodeRunResponse requestTurnRun(
            @AuthenticationPrincipal AppUserDetails principal,
            HttpServletRequest httpRequest,
            @PathVariable("id") Long id,
            @Parameter(description = "실행할 턴 번호(0-based)", example = "0")
            @PathVariable("ordinal") int ordinal
    ) {
        return attemptWebMapper.toCodeRunResponse(
                codeRunService.requestRun(id, owner(principal, httpRequest), ordinal));
    }

    @GetMapping("/{id}/runs")
    @Operation(
            summary = "코드 빌드/실행 목록 조회",
            description = "이 어템프트에서 지금까지 요청한 빌드/실행을 최근 순으로 반환합니다. "
                    + "실행 ID를 잃어버려도 진행 중인 실행과 지난 결과를 되찾을 수 있습니다. "
                    + "응답에는 stdout·stderr가 없으므로 본문은 실행 ID로 단건 조회하세요. "
                    + "제출 완료된 어템프트는 누구나 조회할 수 있고, 진행 중이면 소유자만 조회할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "목록 조회 성공. 실행이 없으면 runs가 빈 배열",
                    content = @Content(schema = @Schema(implementation = CodeRunListResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "어템프트를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public CodeRunListResponse getRuns(
            @AuthenticationPrincipal AppUserDetails principal,
            HttpServletRequest httpRequest,
            @PathVariable("id") Long id
    ) {
        return attemptWebMapper.toCodeRunListResponse(codeRunService.readRuns(id, owner(principal, httpRequest)));
    }

    @GetMapping("/{id}/runs/{runId}")
    @Operation(
            summary = "코드 빌드/실행 결과 조회",
            description = "실행 상태와 결과를 반환합니다. 아직 끝나지 않았으면 status가 QUEUED이고 결과 필드는 모두 null입니다. "
                    + "제출 완료된 어템프트는 누구나 조회할 수 있고, 진행 중이면 소유자만 조회할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "실행 조회 성공",
                    content = @Content(schema = @Schema(implementation = CodeRunResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 어템프트에서 실행 ID를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public CodeRunResponse getRun(
            @AuthenticationPrincipal AppUserDetails principal,
            HttpServletRequest httpRequest,
            @PathVariable("id") Long id,
            @PathVariable("runId") UUID runId
    ) {
        return attemptWebMapper.toCodeRunResponse(codeRunService.readRun(id, owner(principal, httpRequest), runId));
    }

    private AttemptOwner owner(AppUserDetails principal, HttpServletRequest request) {
        return attemptOwnerResolver.resolve(principal, request);
    }
}
