package com.promptstudio.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * GitLab archive.zip을 흉내 낸 테스트용 zip 바이트를 만든다.
 *
 * <p>실제 archive처럼 모든 엔트리에 `<repo>-<sha>/` 루트 프리픽스를 붙이고 중간 디렉토리 엔트리도 함께 넣는다.
 */
public final class ProblemZips {

    private static final String ROOT = "prompt-problem-master-0123456789abcdef/";

    private ProblemZips() {
    }

    /**
     * @param files 루트 프리픽스를 뺀 저장소 기준 경로 → 파일 내용
     */
    public static byte[] archive(Map<String, String> files) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (String directory : directories(files.keySet())) {
                zip.putNextEntry(new ZipEntry(directory));
                zip.closeEntry();
            }

            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(ROOT + file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return buffer.toByteArray();
    }

    private static Set<String> directories(Set<String> paths) {
        Set<String> directories = new LinkedHashSet<>();
        directories.add(ROOT);

        for (String path : paths) {
            int separator = path.indexOf('/');

            while (separator >= 0) {
                directories.add(ROOT + path.substring(0, separator + 1));
                separator = path.indexOf('/', separator + 1);
            }
        }

        return directories;
    }
}
