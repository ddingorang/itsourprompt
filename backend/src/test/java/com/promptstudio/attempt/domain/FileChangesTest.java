package com.promptstudio.attempt.domain;

import com.promptstudio.problem.domain.ProblemFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileChangesTest {

    @Test
    void 새_파일은_ADDED로_분류한다() {
        List<FileChange> changes = FileChanges.diff(
                List.of(),
                List.of(new ProblemFile("a.java", "content"))
        );

        assertThat(changes).containsExactly(new FileChange("a.java", FileChange.ChangeType.ADDED));
    }

    @Test
    void 사라진_파일은_DELETED로_분류한다() {
        List<FileChange> changes = FileChanges.diff(
                List.of(new ProblemFile("a.java", "content")),
                List.of()
        );

        assertThat(changes).containsExactly(new FileChange("a.java", FileChange.ChangeType.DELETED));
    }

    @Test
    void 내용이_바뀐_파일은_MODIFIED로_분류한다() {
        List<FileChange> changes = FileChanges.diff(
                List.of(new ProblemFile("a.java", "before")),
                List.of(new ProblemFile("a.java", "after"))
        );

        assertThat(changes).containsExactly(new FileChange("a.java", FileChange.ChangeType.MODIFIED));
    }

    @Test
    void 내용이_같은_파일은_변경_목록에서_제외한다() {
        List<FileChange> changes = FileChanges.diff(
                List.of(new ProblemFile("a.java", "same")),
                List.of(new ProblemFile("a.java", "same"))
        );

        assertThat(changes).isEmpty();
    }

    @Test
    void 변경_목록은_경로_오름차순으로_정렬된다() {
        List<FileChange> changes = FileChanges.diff(
                List.of(new ProblemFile("b.java", "before")),
                List.of(
                        new ProblemFile("c.java", "new"),
                        new ProblemFile("a.java", "new"),
                        new ProblemFile("b.java", "after")
                )
        );

        assertThat(changes).extracting(FileChange::path).containsExactly("a.java", "b.java", "c.java");
    }
}
