package com.promptstudio.relay.controller;

import com.promptstudio.global.exception.ApiErrorResponse;
import com.promptstudio.global.security.AppUserDetails;
import com.promptstudio.relay.controller.request.RelayTurnRequest;
import com.promptstudio.relay.controller.response.RelayCodeResponse;
import com.promptstudio.relay.controller.response.RelayFeedbackResponse;
import com.promptstudio.relay.controller.response.RelayRoomResponse;
import com.promptstudio.relay.controller.response.RelayTurnListResponse;
import com.promptstudio.relay.controller.response.RelayTurnResponse;
import com.promptstudio.relay.service.RelayFeedbackService;
import com.promptstudio.relay.service.RelayGameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 릴레이 게임 진행 API. 방 관리(개설·입장·퇴장)는 {@link RelayRoomController}에 있다.
 */
@RestController
@RequestMapping("/api/relay/rooms")
@Tag(name = "Relay", description = "릴레이 게임 API — 여러 사람이 한 문제를 순서대로 이어 푼다")
public class RelayGameController {

    private final RelayGameService relayGameService;
    private final RelayFeedbackService relayFeedbackService;
    private final RelayWebMapper relayWebMapper;

    public RelayGameController(
            RelayGameService relayGameService,
            RelayFeedbackService relayFeedbackService,
            RelayWebMapper relayWebMapper
    ) {
        this.relayGameService = relayGameService;
        this.relayFeedbackService = relayFeedbackService;
        this.relayWebMapper = relayWebMapper;
    }

    @PostMapping("/{id}/start")
    @Operation(
            summary = "게임 시작 (방장만)",
            description = "입장을 마감하고 입장 순서대로 좌석을 확정한 뒤 게임을 시작합니다. "
                    + "시작과 동시에 코드 진화를 담을 어템프트가 만들어지고 1번 좌석(방장)의 차례가 됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "시작 성공. 좌석이 확정된 방 상태",
                    content = @Content(schema = @Schema(implementation = RelayRoomResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "방장이 아님",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "방을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 시작되었거나 최소 인원(2명) 미달",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public RelayRoomResponse start(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable("id") Long id
    ) {
        return relayWebMapper.toRoomResponse(relayGameService.start(id, principal.id()));
    }

    @PostMapping("/{id}/turns")
    @Operation(
            summary = "릴레이 턴 전송 (현재 좌석만)",
            description = "현재 차례인 주자가 프롬프트를 전송해 코드를 생성합니다. 생성이 끝날 때까지 "
                    + "블로킹되며(수십 초), 대기 중인 참가자들은 진행 단계를 WebSocket으로 받습니다. "
                    + "성공하면 좌석이 다음으로 넘어가고, 실패하면 같은 좌석이 재시도할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "턴 반영 성공",
                    content = @Content(schema = @Schema(implementation = RelayTurnResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "이 방의 참가자가 아님",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "방을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "내 차례가 아니거나, 다른 턴이 생성 중이거나, 시작 전/종료 후",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "AI 제공자 호출 실패. 같은 좌석이 재시도할 수 있다",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "AI 코드 생성 시간 초과. 같은 좌석이 재시도할 수 있다",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public RelayTurnResponse submitTurn(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable("id") Long id,
            @Valid @RequestBody RelayTurnRequest request
    ) {
        return relayWebMapper.toTurnResponse(
                relayGameService.submitTurn(id, principal.id(), request.prompt()));
    }

    @GetMapping("/{id}/code")
    @Operation(
            summary = "현재 코드 조회 (참가자만)",
            description = "지금까지의 턴을 모두 반영한 파일 전체를 반환합니다. 다음 주자는 이 코드를 보고 "
                    + "프롬프트를 준비합니다. 프롬프트 원문은 포함되지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "코드 조회 성공",
                    content = @Content(schema = @Schema(implementation = RelayCodeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "이 방의 참가자가 아님",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "방을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "아직 게임이 시작되지 않아 코드가 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public RelayCodeResponse getCode(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable("id") Long id
    ) {
        return relayWebMapper.toCodeResponse(relayGameService.getCode(id, principal.id()));
    }

    @GetMapping("/{id}/turns")
    @Operation(
            summary = "턴 이력 조회 (참가자만)",
            description = "완료된 턴들의 채점 점수와 기여도(직전 대비 증가분)를 반환합니다. "
                    + "재접속한 참가자가 놓친 grading.finished 이벤트 대신 스코어보드를 복원하는 경로입니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "이력 조회 성공. 완료된 턴이 없으면 빈 배열",
                    content = @Content(schema = @Schema(implementation = RelayTurnListResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "이 방의 참가자가 아님",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "방을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public RelayTurnListResponse getTurns(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable("id") Long id
    ) {
        return relayWebMapper.toTurnListResponse(relayGameService.getTurns(id, principal.id()));
    }

    @GetMapping("/{id}/feedback")
    @Operation(
            summary = "게임 피드백 조회 (참가자만)",
            description = "턴별(주자별) 프롬프트 피드백과 채점 점수, 게임 전체 총평을 반환합니다. "
                    + "피드백 생성이 끝나기 전(feedback.ready 이전)에는 404입니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "피드백 조회 성공",
                    content = @Content(schema = @Schema(implementation = RelayFeedbackResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "이 방의 참가자가 아님",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "방을 찾을 수 없거나 아직 피드백이 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "아직 게임이 시작되지 않음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public RelayFeedbackResponse getFeedback(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable("id") Long id
    ) {
        return relayWebMapper.toFeedbackResponse(relayFeedbackService.getFeedback(id, principal.id()));
    }

    @PostMapping("/{id}/feedback/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "피드백 생성 재시도 (참가자만)",
            description = "실패한 피드백 생성을 다시 시도합니다. 202 접수 후 결과는 WebSocket의 "
                    + "feedback.ready 또는 feedback.failed로 옵니다. 이미 끝난 방이면 아무 일도 하지 않습니다. "
                    + "방장이 아니어도 걸 수 있습니다 — 방장이 이탈했을 수 있기 때문입니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "재시도 접수"),
            @ApiResponse(
                    responseCode = "403",
                    description = "이 방의 참가자가 아님",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "방을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "피드백 생성 단계가 아님",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public void retryFeedback(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable("id") Long id
    ) {
        relayFeedbackService.retryFeedback(id, principal.id());
    }
}
