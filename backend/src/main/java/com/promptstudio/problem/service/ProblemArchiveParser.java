package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.exception.ProblemSyncFormatException;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GitLab archive.zip을 문제 목록으로 해석한다.
 *
 * <p>archive의 모든 엔트리는 `&lt;repo&gt;-&lt;sha&gt;/` 루트 프리픽스를 가지므로 첫 경로 세그먼트를 떼고 읽는다.
 * 프리픽스를 뗀 뒤의 최상위 디렉토리 하나가 문제 하나이고, 그 디렉토리명이 slug가 된다.
 * 최상위 일반 파일과 `.`으로 시작하는 디렉토리는 문제가 아니므로 무시한다.
 *
 * <p>{@code skeleton/} 아래는 다시 둘로 갈린다. {@code src/test/} 아래는 채점용 테스트라
 * {@link ParsedProblem#testFiles()}로, 나머지는 사용자에게 주는 스켈레톤이라
 * {@link ParsedProblem#files()}로 간다. 갈라놓지 않으면 테스트가 어템프트의 시작 파일이 되어
 * 사용자에게 노출되고 AI가 채점 기준을 직접 고칠 수 있다.
 */
final class ProblemArchiveParser {

    private static final String PROBLEM_YML = "problem.yml";
    private static final String SPEC_MD = "spec.md";
    private static final String SKELETON_PREFIX = "skeleton/";
    /** skeleton 기준 상대경로. 저장소가 Gradle 표준 레이아웃을 쓰므로 테스트는 항상 여기 있다. */
    private static final String TEST_PREFIX = "src/test/";
    private static final String TITLE_KEY = "title";

    private ProblemArchiveParser() {
    }

    static List<ParsedProblem> parse(byte[] archiveZip) {
        List<ParsedProblem> problems = new ArrayList<>();

        for (Map.Entry<String, Map<String, String>> candidate : readCandidates(archiveZip).entrySet()) {
            problems.add(toProblem(candidate.getKey(), candidate.getValue()));
        }

        return problems;
    }

    /**
     * slug → (문제 디렉토리 기준 상대경로 → 내용). 두 단계 모두 경로 알파벳순으로 정렬된다.
     */
    private static Map<String, Map<String, String>> readCandidates(byte[] archiveZip) {
        Map<String, Map<String, String>> candidates = new TreeMap<>();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archiveZip), StandardCharsets.UTF_8)) {
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String path = stripRootPrefix(entry.getName());
                int separator = path.indexOf('/');

                if (separator < 0) {
                    continue;
                }

                String slug = path.substring(0, separator);

                if (slug.startsWith(".")) {
                    continue;
                }

                candidates.computeIfAbsent(slug, key -> new TreeMap<>())
                        .put(path.substring(separator + 1), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return candidates;
    }

    private static String stripRootPrefix(String entryName) {
        int separator = entryName.indexOf('/');

        return separator < 0 ? "" : entryName.substring(separator + 1);
    }

    private static ParsedProblem toProblem(String slug, Map<String, String> entries) {
        String title = readTitle(slug, entries.get(PROBLEM_YML));
        String specMd = entries.get(SPEC_MD);

        if (specMd == null) {
            throw new ProblemSyncFormatException(slug, SPEC_MD + " 파일이 없습니다.");
        }

        List<ProblemFile> files = new ArrayList<>();
        List<ProblemFile> testFiles = new ArrayList<>();

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!entry.getKey().startsWith(SKELETON_PREFIX)) {
                continue;
            }

            String path = entry.getKey().substring(SKELETON_PREFIX.length());

            if (path.startsWith(TEST_PREFIX)) {
                testFiles.add(new ProblemFile(path, entry.getValue()));
            } else {
                files.add(new ProblemFile(path, entry.getValue()));
            }
        }

        // 테스트가 없는 문제는 실행만 하면 되므로 허용한다. 스켈레톤이 없으면 풀 수가 없어 거부한다.
        if (files.isEmpty()) {
            throw new ProblemSyncFormatException(slug, SKELETON_PREFIX + " 아래에 스켈레톤 파일이 없습니다.");
        }

        return new ParsedProblem(slug, title, specMd, files, testFiles);
    }

    private static String readTitle(String slug, String problemYml) {
        if (problemYml == null) {
            throw new ProblemSyncFormatException(slug, PROBLEM_YML + " 파일이 없습니다.");
        }

        if (!(new Yaml().load(problemYml) instanceof Map<?, ?> document)) {
            throw new ProblemSyncFormatException(slug, PROBLEM_YML + "을 매핑으로 읽을 수 없습니다.");
        }

        Object title = document.get(TITLE_KEY);

        if (title == null || String.valueOf(title).isBlank()) {
            throw new ProblemSyncFormatException(slug, PROBLEM_YML + "의 " + TITLE_KEY + "이 비어 있습니다.");
        }

        return String.valueOf(title);
    }
}
