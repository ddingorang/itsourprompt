package com.promptstudio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.attempt.port.CodeGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class GeneratedCodeParser {

    private static final int MAX_LOGGED_RESPONSE_BODY_LENGTH = 4_000;
    private static final Logger log = LoggerFactory.getLogger(GeneratedCodeParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private record AiCodeResponse(List<ProblemFile> files, String aiResponse) {
    }

    private GeneratedCodeParser() {
    }

    static GeneratedCode parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new CodeGenerationException("AI가 빈 응답을 반환했습니다.");
        }

        try {
            AiCodeResponse response = objectMapper.readValue(rawResponse, AiCodeResponse.class);
            validateFiles(response.files());

            if (response.aiResponse() == null || response.aiResponse().isBlank()) {
                throw new CodeGenerationException("AI 응답에 작업 요약이 없습니다.");
            }

            return new GeneratedCode(response.files(), response.aiResponse());
        } catch (JsonProcessingException exception) {
            log.warn("NVIDIA AI가 JSON이 아닌 응답을 반환했습니다. responseBody={}",
                    abbreviate(rawResponse));
            throw new CodeGenerationException("AI가 올바른 JSON 형식의 응답을 반환하지 않았습니다.", exception);
        }
    }

    private static void validateFiles(List<ProblemFile> files) {
        if (files == null || files.isEmpty()) {
            throw new CodeGenerationException("AI 응답에 최종 파일이 없습니다.");
        }

        Set<String> paths = new HashSet<>();

        for (ProblemFile file : files) {
            if (file == null || file.path() == null || file.path().isBlank()) {
                throw new CodeGenerationException("AI 응답에 유효하지 않은 파일 경로가 있습니다.");
            }

            if (file.path().startsWith("/") || file.path().contains("\\") || file.path().contains("..")) {
                throw new CodeGenerationException("AI 응답에 허용되지 않는 파일 경로가 있습니다.");
            }

            if (file.content() == null) {
                throw new CodeGenerationException("AI 응답에 파일 내용이 없습니다.");
            }

            if (!paths.add(file.path())) {
                throw new CodeGenerationException("AI 응답에 중복된 파일 경로가 있습니다.");
            }
        }
    }

    static String abbreviate(String value) {
        if (value == null || value.length() <= MAX_LOGGED_RESPONSE_BODY_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_LOGGED_RESPONSE_BODY_LENGTH) + "... (truncated)";
    }
}
