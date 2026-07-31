package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.domain.SyncState;
import com.promptstudio.problem.exception.ProblemSyncFormatException;
import com.promptstudio.problem.port.ProblemSourceException;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.problem.repository.SyncStateRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeProblemSourceConfiguration;
import com.promptstudio.support.ProblemZips;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(FakeProblemSourceConfiguration.class)
class ProblemSyncServiceTest extends DatabaseTest {

    @Autowired
    private ProblemSyncService problemSyncService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SyncStateRepository syncStateRepository;

    @Autowired
    private FakeProblemSourceConfiguration.FakeProblemSourceClient problemSource;

    @BeforeEach
    void 저장소를_비운다() {
        problemSource.reset();
    }

    @Test
    void 최초_동기화면_문제를_삽입하고_커밋_SHA를_기록한다() {
        problemSource.serve("sha-1", ProblemZips.archive(Map.of(
                "hello-world/problem.yml", "title: Hello World 출력\n",
                "hello-world/spec.md", "# Hello World 출력",
                "hello-world/skeleton/src/main/java/Main.java", "class Main {}"
        )));

        ProblemSyncService.SyncResult result = problemSyncService.sync();

        assertThat(result.created()).isEqualTo(1);
        List<Problem> problems = problemRepository.findAll();
        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst().slug()).isEqualTo("hello-world");
        assertThat(problems.getFirst().title()).isEqualTo("Hello World 출력");
        assertThat(problems.getFirst().specMd()).isEqualTo("# Hello World 출력");
        assertThat(problems.getFirst().files())
                .containsExactly(new ProblemFile("src/main/java/Main.java", "class Main {}"));
        assertThat(problems.getFirst().active()).isTrue();
        assertThat(syncStateRepository.findById(SyncState.ID).orElseThrow().lastCommitSha()).isEqualTo("sha-1");
    }

    @Test
    void 커밋_SHA가_그대로면_아카이브를_내려받지_않는다() {
        problemSource.serve("sha-1", helloWorldArchive("Hello World 출력"));
        problemSyncService.sync();

        ProblemSyncService.SyncResult result = problemSyncService.sync();

        assertThat(result.skipped()).isTrue();
        assertThat(problemSource.downloadCount()).isEqualTo(1);
    }

    @Test
    void 커밋_SHA가_바뀌면_제목과_명세와_파일을_교체한다() {
        problemSource.serve("sha-1", helloWorldArchive("Hello World 출력"));
        problemSyncService.sync();
        Long problemId = problemRepository.findAll().getFirst().id();
        problemSource.serve("sha-2", ProblemZips.archive(Map.of(
                "hello-world/problem.yml", "title: 인사 출력\n",
                "hello-world/spec.md", "# 인사 출력",
                "hello-world/skeleton/src/main/java/Greeter.java", "class Greeter {}"
        )));

        ProblemSyncService.SyncResult result = problemSyncService.sync();

        assertThat(result.updated()).isEqualTo(1);
        Problem updated = problemRepository.findById(problemId).orElseThrow();
        assertThat(updated.title()).isEqualTo("인사 출력");
        assertThat(updated.specMd()).isEqualTo("# 인사 출력");
        assertThat(updated.files())
                .containsExactly(new ProblemFile("src/main/java/Greeter.java", "class Greeter {}"));
        assertThat(syncStateRepository.findById(SyncState.ID).orElseThrow().lastCommitSha()).isEqualTo("sha-2");
    }

    @Test
    void 저장소에서_사라지면_비활성화하고_다시_나타나면_재활성화한다() {
        problemSource.serve("sha-1", helloWorldArchive("Hello World 출력"));
        problemSyncService.sync();
        Long problemId = problemRepository.findAll().getFirst().id();

        problemSource.serve("sha-2", ProblemZips.archive(Map.of(
                "print-ssafy/problem.yml", "title: SSAFY 출력\n",
                "print-ssafy/spec.md", "# SSAFY 출력",
                "print-ssafy/skeleton/src/main/java/Main.java", "class Main {}"
        )));
        ProblemSyncService.SyncResult removal = problemSyncService.sync();

        assertThat(removal.deactivated()).isEqualTo(1);
        assertThat(problemRepository.findById(problemId).orElseThrow().active()).isFalse();

        problemSource.serve("sha-3", helloWorldArchive("Hello World 출력"));
        problemSyncService.sync();

        assertThat(problemRepository.findById(problemId).orElseThrow().active()).isTrue();
    }

    @Test
    void 형식이_잘못된_아카이브면_문제와_커밋_SHA_모두_그대로_둔다() {
        problemSource.serve("sha-1", helloWorldArchive("Hello World 출력"));
        problemSyncService.sync();
        problemSource.serve("sha-2", ProblemZips.archive(Map.of(
                "hello-world/problem.yml", "title: 인사 출력\n",
                "hello-world/spec.md", "# 인사 출력",
                "broken/spec.md", "# 명세만 있는 디렉토리"
        )));

        assertThatThrownBy(() -> problemSyncService.sync())
                .isInstanceOf(ProblemSyncFormatException.class);

        assertThat(problemRepository.findAll())
                .extracting(Problem::title)
                .containsExactly("Hello World 출력");
        assertThat(syncStateRepository.findById(SyncState.ID).orElseThrow().lastCommitSha()).isEqualTo("sha-1");
    }

    @Test
    void 저장소_호출이_실패하면_문제와_커밋_SHA_모두_그대로_둔다() {
        problemSource.serve("sha-1", helloWorldArchive("Hello World 출력"));
        problemSyncService.sync();
        problemSource.serve("sha-2", helloWorldArchive("인사 출력"));
        problemSource.failNextWith(new ProblemSourceException("문제 저장소 아카이브 다운로드에 실패했습니다."));

        assertThatThrownBy(() -> problemSyncService.sync())
                .isInstanceOf(ProblemSourceException.class);

        assertThat(problemRepository.findAll())
                .extracting(Problem::title)
                .containsExactly("Hello World 출력");
        assertThat(syncStateRepository.findById(SyncState.ID).orElseThrow().lastCommitSha()).isEqualTo("sha-1");
    }

    @Test
    void slug가_없는_레거시_문제는_건드리지_않는다() {
        Problem legacy = problemRepository.save(new Problem(null, "레거시 문제", "# 레거시", List.of(
                new ProblemFile("src/main/java/Main.java", "class Main {}")
        ), List.of()));
        problemSource.serve("sha-1", helloWorldArchive("Hello World 출력"));

        problemSyncService.sync();

        Problem found = problemRepository.findById(legacy.id()).orElseThrow();
        assertThat(found.slug()).isNull();
        assertThat(found.title()).isEqualTo("레거시 문제");
        assertThat(found.active()).isTrue();
    }

    private byte[] helloWorldArchive(String title) {
        return ProblemZips.archive(Map.of(
                "hello-world/problem.yml", "title: " + title + "\n",
                "hello-world/spec.md", "# " + title,
                "hello-world/skeleton/src/main/java/Main.java", "class Main {}"
        ));
    }
}
