package com.promptstudio.attempt.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "프롬프트 피드백 결과")
public record FeedbackResponse(
        @Schema(description = "턴별 피드백. 턴별 피드백 이전에 제출된 어템프트는 빈 배열이다.")
        List<TurnFeedback> turns,

        @Schema(description = "세션 전체에 대한 피드백 (Markdown)")
        String overallMd,

        @Schema(description = "세션 전체의 작업 방식에 이름을 붙인 피드백 (Markdown). "
                + "pattern 피드백 이전에 제출된 어템프트는 null이다.")
        String patternOverallMd,

        @Schema(description = "다음 문제의 상시 지시 파일에 붙여넣을 규칙 한 줄. "
                + "이 세션이 어느 신호에도 걸리지 않으면 null이며, 그때는 화면에 아무것도 올리지 않는다.")
        CarryLineResponse carry
) {

    @Schema(description = "한 턴의 프롬프트 피드백")
    public record TurnFeedback(
            @Schema(description = "턴 번호. 1부터 시작한다.", example = "1")
            int turn,

            @Schema(description = "해당 턴 프롬프트에 대한 피드백 (Markdown)")
            String feedbackMd,

            @Schema(description = "해당 턴의 작업 방식에 이름을 붙인 피드백 (Markdown). "
                    + "pattern 피드백 이전에 제출된 어템프트는 null이다.")
            String patternMd
    ) {
    }

    /**
     * 두 렌즈의 마크다운과 섞지 않고 별도 필드로 나간다. 렌즈는 LLM이 쓰지만 이 줄은 전부 계산이라,
     * 피드백 프롬프트를 통째로 갈아도 이 값은 무사해야 한다.
     */
    @Schema(description = "제출 후 가져갈 규칙 한 줄")
    public record CarryLineResponse(
            @Schema(description = "이 줄을 고른 신호의 키. 화면에 쓰지 않는다.",
                    example = "turn_has_no_finished_run")
            String signal,

            @Schema(description = "지시 파일에 그대로 붙여넣을 규칙 한 줄. 도구 중립이라 파일 이름도 "
                    + "도구 이름도 담지 않는다.")
            String rule,

            @Schema(description = "왜 이 줄인지 (Markdown). 세션에서 센 값으로 조립한다.")
            String reason
    ) {
    }
}
