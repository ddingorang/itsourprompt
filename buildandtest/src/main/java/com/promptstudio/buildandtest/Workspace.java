package com.promptstudio.buildandtest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 실행 한 건이 쓰는 임시 디렉토리. {@link AutoCloseable}이라 try-with-resources로 항상 정리된다.
 */
final class Workspace implements AutoCloseable {

    private static final String OUTPUT_DIR = "out";
    private static final String SOURCE_LIST = "sources.txt";

    private final Path root;

    private Workspace(Path root) {
        this.root = root;
    }

    static Workspace create() throws IOException {
        return new Workspace(Files.createTempDirectory("run-"));
    }

    Path root() {
        return root;
    }

    Path outputDir() {
        return root.resolve(OUTPUT_DIR);
    }

    /**
     * 파일을 기록하고 컴파일 대상 .java 목록을 반환한다.
     *
     * <p>경로는 와이어로 받은 값이라 반드시 여기서 다시 검증한다. 백엔드 {@code GeneratedCodeParser}의
     * 검증은 다른 프로세스에 있어 이 워커를 보호해주지 않는다.
     *
     * @return 작업 디렉토리 기준 상대 경로로 표현한 .java 파일 목록
     */
    List<String> writeFiles(List<RunRequestMessage.RunFileMessage> files) throws IOException {
        Files.createDirectories(outputDir());
        List<String> javaSources = new ArrayList<>();

        for (RunRequestMessage.RunFileMessage file : files) {
            String relativePath = validated(file.path());
            Path target = root.resolve(relativePath).normalize();

            Files.createDirectories(target.getParent());
            Files.writeString(target, file.content() == null ? "" : file.content(), StandardCharsets.UTF_8);

            if (relativePath.endsWith(".java")) {
                javaSources.add(relativePath);
            }
        }

        return javaSources;
    }

    /**
     * javac에 넘길 인자 파일을 만든다. 파일이 많아져도 커맨드라인 길이 제한에 걸리지 않게 한다.
     */
    String writeSourceList(List<String> javaSources) throws IOException {
        Files.write(root.resolve(SOURCE_LIST), javaSources, StandardCharsets.UTF_8);

        return "@" + SOURCE_LIST;
    }

    private String validated(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("빈 파일 경로입니다.");
        }

        // 백엔드와 같은 규칙: 절대 경로, 윈도우 구분자, 상위 경로 참조를 모두 거부한다.
        if (path.startsWith("/") || path.contains("\\") || path.contains("..")) {
            throw new IllegalArgumentException("허용되지 않는 파일 경로입니다: " + path);
        }

        Path target = root.resolve(path).normalize();

        // normalize() 이후에도 작업 디렉토리를 벗어나면 거부한다(심볼릭 링크가 아닌 경로 조작 방어).
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("작업 디렉토리를 벗어나는 경로입니다: " + path);
        }

        return path;
    }

    @Override
    public void close() throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
