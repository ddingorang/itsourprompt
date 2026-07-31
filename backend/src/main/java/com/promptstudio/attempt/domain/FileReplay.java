package com.promptstudio.attempt.domain;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 시작 스켈레톤에 턴별 변경을 순서대로 재생해 현재 상태를 만든다. 저장된 것은 base와 턴뿐이고 현재 상태는 여기서 파생한다.
 */
public final class FileReplay {

    private FileReplay() {
    }

    public static <T> List<ProblemFile> head(
            List<ProblemFile> baseFiles,
            List<T> turns,
            Function<T, List<FileChange>> changesOf
    ) {
        Map<String, String> contents = new LinkedHashMap<>();

        for (ProblemFile file : baseFiles) {
            contents.put(file.path(), file.content());
        }

        for (T turn : turns) {
            for (FileChange change : changesOf.apply(turn)) {
                apply(contents, change);
            }
        }

        List<ProblemFile> head = new ArrayList<>();

        for (Map.Entry<String, String> content : contents.entrySet()) {
            head.add(new ProblemFile(content.getKey(), content.getValue()));
        }

        return Collections.unmodifiableList(head);
    }

    /**
     * 기존 경로를 다시 담으면 자리를 유지하고, 새 경로는 뒤에 붙는다.
     */
    private static void apply(Map<String, String> contents, FileChange change) {
        if (change.type() == FileChange.ChangeType.DELETED) {
            contents.remove(change.path());

            return;
        }

        contents.put(change.path(), change.content());
    }
}
