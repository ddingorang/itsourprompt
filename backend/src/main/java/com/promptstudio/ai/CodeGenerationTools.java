package com.promptstudio.ai;

import com.promptstudio.attempt.domain.ToolCallEntry;
import com.promptstudio.problem.domain.ProblemFile;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 코드 생성 요청 하나가 쓰는 파일 작업본과 툴 3종.
 *
 * <p>요청마다 새 인스턴스를 만들어 동시 요청을 격리한다. 매니저가 툴을 순차 실행하므로 동기화는 없다.
 *
 * <p>미존재 경로 같은 인자 오류는 예외가 아니라 에러 문자열로 돌려준다 — 모델이 다음 라운드에서
 * 스스로 고치게 하고, 그 라운드도 상한에 카운트된다.
 */
final class CodeGenerationTools {

    private static final String LIST_FILES = ToolCallEntry.LIST_FILES;
    private static final String READ_FILE = ToolCallEntry.READ_FILE;
    private static final String EDIT_FILE = ToolCallEntry.EDIT_FILE;
    private static final String FILE_NOT_FOUND = "파일을 찾을 수 없습니다: %s. " + LIST_FILES + "로 파일 목록을 확인하세요.";
    private static final String EDIT_SUCCESS = "ok";
    private static final Set<String> PROTECTED_BUILD_FILE_NAMES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties"
    );
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([^;]+);");

    /**
     * turn_tool_call.path 컬럼(VARCHAR(500)) 한도. 모델이 지어낸 긴 경로가 트레이스에 그대로 실리면
     * 에러 문자열로 자가 수정시킨 턴이 저장 단계에서 죽는다 — 기록 시점에 절단한다.
     */
    private static final int MAX_TRACE_PATH_LENGTH = 500;

    private final Map<String, String> workingCopy = new LinkedHashMap<>();
    private final List<ToolCallEntry> trace = new ArrayList<>();
    private final Set<String> editedPaths = new LinkedHashSet<>();

    CodeGenerationTools(List<ProblemFile> files) {
        for (ProblemFile file : files) {
            workingCopy.put(file.path(), file.content());
        }
    }

    record ReadFileRequest(String path) {
    }

    record EditFileRequest(String path, String content) {
    }

    List<ToolCallback> callbacks() {
        return List.of(
                FunctionToolCallback.builder(LIST_FILES, (Supplier<String>) this::listFiles)
                        .description("현재 프로젝트의 모든 파일 경로를 한 줄에 하나씩 반환한다.")
                        .build(),
                FunctionToolCallback.builder(READ_FILE, (Function<ReadFileRequest, String>) this::readFile)
                        .description("파일 하나의 현재 전체 내용을 반환한다.")
                        .inputType(ReadFileRequest.class)
                        .build(),
                FunctionToolCallback.builder(EDIT_FILE, (Function<EditFileRequest, String>) this::editFile)
                        .description("기존 파일의 전체 내용을 교체한다. 새 파일 생성 불가.")
                        .inputType(EditFileRequest.class)
                        .build()
        );
    }

    private String listFiles() {
        trace.add(new ToolCallEntry(LIST_FILES, null));

        return String.join("\n", workingCopy.keySet());
    }

    private String readFile(ReadFileRequest request) {
        trace.add(new ToolCallEntry(READ_FILE, truncateForTrace(request.path())));
        String content = workingCopy.get(request.path());

        if (content == null) {
            return FILE_NOT_FOUND.formatted(request.path());
        }

        return content;
    }

    private String editFile(EditFileRequest request) {
        trace.add(new ToolCallEntry(EDIT_FILE, truncateForTrace(request.path())));

        if (!workingCopy.containsKey(request.path())) {
            return FILE_NOT_FOUND.formatted(request.path());
        }

        String violation = policyViolation(request);

        if (violation != null) {
            return violation;
        }

        workingCopy.put(request.path(), request.content());
        editedPaths.add(request.path());

        return EDIT_SUCCESS;
    }

    private String policyViolation(EditFileRequest request) {
        if (isProtectedBuildFile(request.path())) {
            return "이 수정은 적용하지 않았어요. " + request.path()
                    + "은(는) 의존성·빌드 설정 파일이라 변경할 수 없습니다. "
                    + "현재 문제에서 수정해야 하는 Java 소스 파일을 선택해 다시 작성해 주세요.";
        }

        Matcher matcher = IMPORT_PATTERN.matcher(request.content());

        while (matcher.find()) {
            String importedType = matcher.group(1).trim();

            if (!importedType.startsWith("java.")) {
                return "이 수정은 적용하지 않았어요. " + importedType
                        + "은(는) 외부 라이브러리 import입니다. 이 문제에서는 Java 표준 라이브러리(java.*)만 사용할 수 있습니다. "
                        + "외부 라이브러리 없이 표준 Java 코드로 다시 작성해 주세요.";
            }
        }

        return null;
    }

    private boolean isProtectedBuildFile(String path) {
        String normalizedPath = path.replace('\\', '/');
        int lastSlash = normalizedPath.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;

        return PROTECTED_BUILD_FILE_NAMES.contains(fileName);
    }

    private static String truncateForTrace(String path) {
        if (path == null || path.length() <= MAX_TRACE_PATH_LENGTH) {
            return path;
        }

        return path.substring(0, MAX_TRACE_PATH_LENGTH);
    }

    List<ProblemFile> currentFiles() {
        return workingCopy.entrySet().stream()
                .map(entry -> new ProblemFile(entry.getKey(), entry.getValue()))
                .toList();
    }

    List<ToolCallEntry> trace() {
        return List.copyOf(trace);
    }

    long editedFileCount() {
        return editedPaths.size();
    }
}
