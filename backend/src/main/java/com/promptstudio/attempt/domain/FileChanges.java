package com.promptstudio.attempt.domain;

import com.promptstudio.problem.domain.ProblemFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class FileChanges {

    private FileChanges() {
    }

    public static List<FileChange> diff(List<ProblemFile> originalFiles, List<ProblemFile> finalFiles) {
        Map<String, String> originalContents = toContentByPath(originalFiles);
        Map<String, String> finalContents = toContentByPath(finalFiles);
        TreeSet<String> paths = new TreeSet<>();
        paths.addAll(originalContents.keySet());
        paths.addAll(finalContents.keySet());

        List<FileChange> changes = new ArrayList<>();

        for (String path : paths) {
            boolean existedBefore = originalContents.containsKey(path);
            boolean existsNow = finalContents.containsKey(path);

            if (!existedBefore) {
                changes.add(new FileChange(path, FileChange.ChangeType.ADDED, finalContents.get(path)));
            } else if (!existsNow) {
                changes.add(new FileChange(path, FileChange.ChangeType.DELETED, null));
            } else if (!originalContents.get(path).equals(finalContents.get(path))) {
                changes.add(new FileChange(path, FileChange.ChangeType.MODIFIED, finalContents.get(path)));
            }
        }

        return changes;
    }

    private static Map<String, String> toContentByPath(List<ProblemFile> files) {
        Map<String, String> contents = new HashMap<>();

        for (ProblemFile file : files) {
            contents.put(file.path(), file.content());
        }

        return contents;
    }
}
