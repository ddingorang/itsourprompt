package com.promptstudio.buildandtest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceTest {

    @Test
    void 계층_구조를_그대로_기록하고_java_목록만_돌려준다() throws IOException {
        try (Workspace workspace = Workspace.create()) {
            List<String> sources = workspace.writeFiles(List.of(
                    new RunRequestMessage.RunFileMessage("src/main/java/com/deep/nested/Foo.java", "class Foo {}"),
                    new RunRequestMessage.RunFileMessage("README.md", "문서")));

            assertThat(sources).containsExactly("src/main/java/com/deep/nested/Foo.java");
            assertThat(workspace.root().resolve("src/main/java/com/deep/nested/Foo.java")).exists();
            assertThat(workspace.root().resolve("README.md")).exists();
        }
    }

    @Test
    void 닫으면_임시_디렉토리가_남지_않는다() throws IOException {
        Path root;

        try (Workspace workspace = Workspace.create()) {
            root = workspace.root();
            workspace.writeFiles(List.of(
                    new RunRequestMessage.RunFileMessage("src/main/java/Main.java", "class Main {}")));
            assertThat(root).exists();
        }

        assertThat(root).doesNotExist();
    }

    @Test
    void 작업_디렉토리를_벗어나는_경로는_거부한다() throws IOException {
        try (Workspace workspace = Workspace.create()) {
            assertThatThrownBy(() -> workspace.writeFiles(List.of(
                    new RunRequestMessage.RunFileMessage("a/../../b/Main.java", "class Main {}"))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 윈도우_구분자가_섞인_경로는_거부한다() throws IOException {
        try (Workspace workspace = Workspace.create()) {
            assertThatThrownBy(() -> workspace.writeFiles(List.of(
                    new RunRequestMessage.RunFileMessage("src\\main\\Main.java", "class Main {}"))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 내용이_null이면_빈_파일로_기록한다() throws IOException {
        try (Workspace workspace = Workspace.create()) {
            workspace.writeFiles(List.of(new RunRequestMessage.RunFileMessage("Empty.java", null)));

            assertThat(Files.readString(workspace.root().resolve("Empty.java"))).isEmpty();
        }
    }
}
