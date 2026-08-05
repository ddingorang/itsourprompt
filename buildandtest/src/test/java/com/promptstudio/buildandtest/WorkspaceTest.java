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
            Workspace.Sources sources = workspace.writeFiles(List.of(
                    new RunRequestMessage.RunFileMessage("src/main/java/com/deep/nested/Foo.java", "class Foo {}"),
                    new RunRequestMessage.RunFileMessage("README.md", "문서")), List.of());

            assertThat(sources.main()).containsExactly("src/main/java/com/deep/nested/Foo.java");
            assertThat(sources.hasTests()).isFalse();
            assertThat(workspace.root().resolve("src/main/java/com/deep/nested/Foo.java")).exists();
            assertThat(workspace.root().resolve("README.md")).exists();
        }
    }

    @Test
    void 테스트_파일은_별도_목록으로_돌려주고_전체_목록에도_넣는다() throws IOException {
        try (Workspace workspace = Workspace.create()) {
            Workspace.Sources sources = workspace.writeFiles(
                    List.of(new RunRequestMessage.RunFileMessage("src/main/java/Main.java", "class Main {}")),
                    List.of(new RunRequestMessage.RunFileMessage("src/test/java/MainTest.java", "class MainTest {}")));

            assertThat(sources.main()).containsExactly("src/main/java/Main.java");
            assertThat(sources.test()).containsExactly("src/test/java/MainTest.java");
            assertThat(sources.hasTests()).isTrue();
            assertThat(sources.all())
                    .containsExactly("src/main/java/Main.java", "src/test/java/MainTest.java");
            assertThat(workspace.root().resolve("src/test/java/MainTest.java")).exists();
        }
    }

    /**
     * 테스트가 skeleton 안에 있던 시절에 시작된 어템프트는 같은 경로를 제출 파일로 갖고 있다.
     * 그 경우에도 채점 기준이 이겨야 한다.
     */
    @Test
    void 경로가_겹치면_테스트가_제출_파일을_덮어쓴다() throws IOException {
        try (Workspace workspace = Workspace.create()) {
            String path = "src/test/java/MainTest.java";
            workspace.writeFiles(
                    List.of(new RunRequestMessage.RunFileMessage(path, "// AI가 비워버린 테스트")),
                    List.of(new RunRequestMessage.RunFileMessage(path, "// 진짜 채점 테스트")));

            assertThat(Files.readString(workspace.root().resolve(path))).isEqualTo("// 진짜 채점 테스트");
        }
    }

    @Test
    void 테스트_목록이_null이어도_처리한다() throws IOException {
        try (Workspace workspace = Workspace.create()) {
            Workspace.Sources sources = workspace.writeFiles(
                    List.of(new RunRequestMessage.RunFileMessage("Main.java", "class Main {}")), null);

            assertThat(sources.main()).containsExactly("Main.java");
            assertThat(sources.hasTests()).isFalse();
        }
    }

    @Test
    void 닫으면_임시_디렉토리가_남지_않는다() throws IOException {
        Path root;

        try (Workspace workspace = Workspace.create()) {
            root = workspace.root();
            workspace.writeFiles(List.of(
                    new RunRequestMessage.RunFileMessage("src/main/java/Main.java", "class Main {}")), List.of());
            assertThat(root).exists();
        }

        assertThat(root).doesNotExist();
    }

    @Test
    void 작업_디렉토리를_벗어나는_경로는_거부한다() throws IOException {
        try (Workspace workspace = Workspace.create()) {
            assertThatThrownBy(() -> workspace.writeFiles(List.of(
                    new RunRequestMessage.RunFileMessage("a/../../b/Main.java", "class Main {}")), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 윈도우_구분자가_섞인_경로는_거부한다() throws IOException {
        try (Workspace workspace = Workspace.create()) {
            assertThatThrownBy(() -> workspace.writeFiles(List.of(
                    new RunRequestMessage.RunFileMessage("src\\main\\Main.java", "class Main {}")), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 내용이_null이면_빈_파일로_기록한다() throws IOException {
        try (Workspace workspace = Workspace.create()) {
            workspace.writeFiles(List.of(new RunRequestMessage.RunFileMessage("Empty.java", null)), List.of());

            assertThat(Files.readString(workspace.root().resolve("Empty.java"))).isEmpty();
        }
    }
}
