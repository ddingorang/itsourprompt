package com.promptstudio.relay.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 어템프트 응답을 재사용하지 않는 이유: 그쪽에는 턴별 프롬프트 원문이 실려 있는데,
 * "이전 주자의 프롬프트를 보여줄 것인가"는 방 옵션으로 열어 둔 결정이다. 릴레이가 노출하는 것은
 * 코드 그 자체뿐이어야 나중에 어느 쪽으로든 갈 수 있다.
 */
@Schema(description = "릴레이 방의 현재 코드. 지금까지의 턴을 모두 반영한 파일 전체")
public record RelayCodeResponse(
        @Schema(description = "지금까지 반영된 턴 수. 0이면 시작 스켈레톤 그대로", example = "3")
        int appliedTurns,
        @Schema(description = "현재 파일 전체")
        List<RelayFileResponse> files
) {

    @Schema(description = "파일 하나")
    public record RelayFileResponse(
            @Schema(description = "파일 경로", example = "src/main/java/Main.java")
            String path,
            @Schema(description = "파일 전체 내용")
            String content
    ) {
    }
}
