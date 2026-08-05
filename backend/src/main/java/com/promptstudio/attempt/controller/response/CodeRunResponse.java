package com.promptstudio.attempt.controller.response;

import com.promptstudio.attempt.domain.CodeRunCaseStatus;
import com.promptstudio.attempt.domain.CodeRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "코드 빌드/실행 상태")
public record CodeRunResponse(
        @Schema(description = "실행 ID", example = "3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071")
        UUID runId,
        @Schema(
                description = "실행한 코드가 몇 번째 턴의 것인지(0-based). 턴을 적용하지 않은 시작 스켈레톤을 "
                        + "실행했거나 턴 단위 기록 이전의 실행이면 null",
                example = "2"
        )
        Integer turnOrdinal,
        @Schema(description = "실행 상태", example = "QUEUED")
        CodeRunStatus status,
        @Schema(description = "java 프로세스 종료 코드. 종료 전에는 null", example = "0")
        Integer exitCode,
        @Schema(description = "표준 출력. 종료 전에는 null")
        String stdout,
        @Schema(description = "표준 에러. 컴파일 오류 메시지가 여기 담긴다. 종료 전에는 null")
        String stderr,
        @Schema(description = "실행에 걸린 시간(ms). 종료 전에는 null", example = "1840")
        Long durationMs,
        @Schema(
                description = "채점 테스트의 케이스별 결과. 테스트를 돌리지 않은 실행이거나 케이스 기록 "
                        + "도입 이전의 실행이면 빈 배열이다. 빈 배열은 '테스트 0개 통과'가 아니라 "
                        + "'기록 없음'이며, 통과 여부는 status가 진실이다."
        )
        List<CodeRunCaseResponse> cases
) {

    @Schema(description = "채점 테스트 한 건의 결과")
    public record CodeRunCaseResponse(
            @Schema(description = "테스트 클래스명. 리포트에 없으면 null", example = "PhoneNumberFormatterTest")
            String className,
            @Schema(description = "테스트 이름", example = "지역번호_2자리와_국번_3자리를_마스킹한다()")
            String name,
            @Schema(description = "PASSED | FAILED | ERROR | SKIPPED", example = "FAILED")
            CodeRunCaseStatus status,
            @Schema(
                    description = "실패·에러 사유 한 줄. 통과·스킵이면 null. 전체 스택트레이스는 stdout에 있다",
                    example = "expected: <02-***-4567> but was: <02-1****567>"
            )
            String message,
            @Schema(description = "케이스 소요 시간(ms). 리포트에 시간이 없으면 null", example = "17")
            Long durationMs
    ) {
    }
}
