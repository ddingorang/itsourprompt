package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.exception.ProblemSyncFormatException;
import com.promptstudio.support.ProblemZips;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemArchiveParserTest {

    @Test
    void 문제_디렉토리를_slug와_제목과_명세와_스켈레톤으로_해석한다() {
        byte[] archive = ProblemZips.archive(Map.of(
                "hello-world/problem.yml", "title: Hello World 출력\n",
                "hello-world/spec.md", "# Hello World 출력\n\n표준 출력으로 인사하세요.",
                "hello-world/skeleton/src/main/java/Main.java", "class Main {}",
                "hello-world/skeleton/README.md", "# 안내"
        ));

        List<ParsedProblem> problems = ProblemArchiveParser.parse(archive);

        assertThat(problems).hasSize(1);
        ParsedProblem problem = problems.getFirst();
        assertThat(problem.slug()).isEqualTo("hello-world");
        assertThat(problem.title()).isEqualTo("Hello World 출력");
        assertThat(problem.specMd()).isEqualTo("# Hello World 출력\n\n표준 출력으로 인사하세요.");
        assertThat(problem.files()).containsExactly(
                new ProblemFile("README.md", "# 안내"),
                new ProblemFile("src/main/java/Main.java", "class Main {}")
        );
    }

    @Test
    void 최상위_일반_파일과_점으로_시작하는_디렉토리는_무시한다() {
        byte[] archive = ProblemZips.archive(Map.of(
                "README.md", "# 저장소 안내",
                ".gitlab-ci.yml", "stages: []",
                ".github/workflows/ci.yml", "on: push",
                "hello-world/problem.yml", "title: Hello World 출력\n",
                "hello-world/spec.md", "# 명세",
                "hello-world/skeleton/Main.java", "class Main {}"
        ));

        assertThat(ProblemArchiveParser.parse(archive))
                .extracting(ParsedProblem::slug)
                .containsExactly("hello-world");
    }

    @Test
    void problem_yml이_없으면_예외를_던진다() {
        byte[] archive = ProblemZips.archive(Map.of(
                "hello-world/spec.md", "# 명세",
                "hello-world/skeleton/Main.java", "class Main {}"
        ));

        assertThatThrownBy(() -> ProblemArchiveParser.parse(archive))
                .isInstanceOf(ProblemSyncFormatException.class)
                .hasMessageContaining("hello-world")
                .hasMessageContaining("problem.yml");
    }

    @Test
    void title이_비어_있으면_예외를_던진다() {
        byte[] archive = ProblemZips.archive(Map.of(
                "hello-world/problem.yml", "title: \"   \"\n",
                "hello-world/spec.md", "# 명세",
                "hello-world/skeleton/Main.java", "class Main {}"
        ));

        assertThatThrownBy(() -> ProblemArchiveParser.parse(archive))
                .isInstanceOf(ProblemSyncFormatException.class)
                .hasMessageContaining("hello-world")
                .hasMessageContaining("title");
    }

    @Test
    void spec_md가_없으면_예외를_던진다() {
        byte[] archive = ProblemZips.archive(Map.of(
                "hello-world/problem.yml", "title: Hello World 출력\n",
                "hello-world/skeleton/Main.java", "class Main {}"
        ));

        assertThatThrownBy(() -> ProblemArchiveParser.parse(archive))
                .isInstanceOf(ProblemSyncFormatException.class)
                .hasMessageContaining("hello-world")
                .hasMessageContaining("spec.md");
    }

    @Test
    void skeleton에_파일이_없으면_예외를_던진다() {
        byte[] archive = ProblemZips.archive(Map.of(
                "hello-world/problem.yml", "title: Hello World 출력\n",
                "hello-world/spec.md", "# 명세"
        ));

        assertThatThrownBy(() -> ProblemArchiveParser.parse(archive))
                .isInstanceOf(ProblemSyncFormatException.class)
                .hasMessageContaining("hello-world")
                .hasMessageContaining("skeleton");
    }
}
