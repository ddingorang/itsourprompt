package com.promptstudio.problem.controller.request;

import com.promptstudio.problem.domain.FileChange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "프롬프트 피드백 제출 요청")
public record SubmitRequest(
        @NotBlank
        @Size(max = 10_000)
        @Schema(description = "사용자가 실행에 사용한 프롬프트", example = "Hello, World!를 출력하도록 코드를 완성해줘")
        String prompt,

        @NotBlank
        @Size(max = 20_000)
        @Schema(description = "직전 실행에서 받은 AI 작업 요약", example = "Main.java의 TODO를 완료했습니다.")
        String aiResponse,

        @NotNull
        @Size(max = 100)
        @Valid
        @Schema(description = "직전 실행에서 변경된 파일 목록")
        List<ChangedFile> changedFiles
) {

    public record ChangedFile(
            @NotBlank
            @Size(max = 500)
            @Schema(description = "변경된 파일 경로", example = "src/main/java/Main.java")
            String path,

            @NotNull
            @Schema(description = "파일 변경 유형", example = "MODIFIED")
            FileChange.ChangeType changeType
    ) {
    }
}
