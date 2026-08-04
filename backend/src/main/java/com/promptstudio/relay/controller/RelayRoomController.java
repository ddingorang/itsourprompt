package com.promptstudio.relay.controller;

import com.promptstudio.global.exception.ApiErrorResponse;
import com.promptstudio.global.security.AppUserDetails;
import com.promptstudio.relay.controller.request.CreateRelayRoomRequest;
import com.promptstudio.relay.controller.response.RelayRoomListResponse;
import com.promptstudio.relay.controller.response.RelayRoomResponse;
import com.promptstudio.relay.service.RelayRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 릴레이 게임 방 API.
 *
 * <p>어템프트 API와 달리 게스트를 받지 않는다. 게임은 여러 사람의 순서를 몇 분에 걸쳐 지켜야 하는데,
 * 게스트 세션은 만료·재발급으로 신원이 바뀔 수 있어 좌석의 주인을 보장할 수 없다.
 * 그래서 사용자 ID는 항상 인증 주체에서만 꺼낸다.
 */
@RestController
@RequestMapping("/api/relay/rooms")
@Tag(name = "Relay", description = "릴레이 게임 API — 여러 사람이 한 문제를 순서대로 이어 푼다")
public class RelayRoomController {

    private final RelayRoomService relayRoomService;
    private final RelayWebMapper relayWebMapper;

    public RelayRoomController(RelayRoomService relayRoomService, RelayWebMapper relayWebMapper) {
        this.relayRoomService = relayRoomService;
        this.relayWebMapper = relayWebMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "릴레이 방 개설",
            description = "입장을 받는 방을 만듭니다. 방을 만든 사람은 개설과 동시에 첫 번째 참가자가 되므로 "
                    + "1번 좌석은 항상 방장입니다. 방장이 시작 전에 나가면 남은 참가자 중 가장 먼저 입장한 "
                    + "사람에게 시작 권한이 넘어갑니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "방 개설 성공",
                    content = @Content(schema = @Schema(implementation = RelayRoomResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요함",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "문제를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "비활성 문제",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public RelayRoomResponse openRoom(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody CreateRelayRoomRequest request
    ) {
        return relayWebMapper.toRoomResponse(relayRoomService.openRoom(
                request.problemId(),
                principal.id(),
                request.totalLaps(),
                request.maxParticipants()
        ));
    }

    @GetMapping
    @Operation(
            summary = "입장 가능한 방 목록",
            description = "입장을 받는 중(WAITING)이고 사람이 있는 방들을 최신 개설 순으로 반환합니다. "
                    + "로비가 이 목록에서 방을 골라 입장합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "목록 조회 성공. 없으면 rooms가 빈 배열",
                    content = @Content(schema = @Schema(implementation = RelayRoomListResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요함",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public RelayRoomListResponse listRooms() {
        return relayWebMapper.toRoomListResponse(relayRoomService.listJoinableRooms());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "릴레이 방 조회",
            description = "방의 현재 상태와 참가자 목록을 반환합니다. 재접속 시 이 응답만으로 화면을 복원할 수 "
                    + "있어야 하므로 진행 단계와 좌석 정보가 모두 들어 있습니다. 입장 전에도 조회할 수 있습니다 "
                    + "— 어떤 문제인지와 몇 명이 차 있는지를 보고 들어와야 하기 때문입니다. 다만 실시간 게임 "
                    + "상태를 받는 WebSocket은 참가자만 붙을 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "방 조회 성공",
                    content = @Content(schema = @Schema(implementation = RelayRoomResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요함",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "방을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public RelayRoomResponse getRoom(@PathVariable("id") Long id) {
        return relayWebMapper.toRoomResponse(relayRoomService.getRoom(id));
    }

    @PostMapping("/{id}/participants")
    @Operation(
            summary = "릴레이 방 입장",
            description = "방에 입장합니다. 입장 순서가 그대로 풀이 순서가 되며, 좌석 번호는 게임 시작 시점에 "
                    + "부여됩니다. 이미 참가자인 상태로 다시 요청하면 중복 입장 없이 현재 상태를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "입장 성공. 이미 참가자였으면 현재 상태를 그대로 반환",
                    content = @Content(schema = @Schema(implementation = RelayRoomResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요함",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "방을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "정원이 가득 찼거나 이미 게임이 시작됨",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public RelayRoomResponse join(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable("id") Long id
    ) {
        return relayWebMapper.toRoomResponse(relayRoomService.join(id, principal.id()));
    }

    @DeleteMapping("/{id}/participants/me")
    @Operation(
            summary = "릴레이 방 퇴장",
            description = "방에서 나갑니다. 시작 전에는 참가 기록이 지워지고, 게임 중에는 좌석이 남습니다 — "
                    + "남은 좌석의 턴은 제한시간이 지나면 건너뜁니다. 방장이 시작 전에 나가면 남은 참가자 중 "
                    + "가장 먼저 입장한 사람에게 시작 권한이 넘어갑니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "퇴장 성공",
                    content = @Content(schema = @Schema(implementation = RelayRoomResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요함",
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
            )
    })
    public RelayRoomResponse leave(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable("id") Long id
    ) {
        return relayWebMapper.toRoomResponse(relayRoomService.leave(id, principal.id()));
    }
}
