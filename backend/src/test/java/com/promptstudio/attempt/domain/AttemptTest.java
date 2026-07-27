package com.promptstudio.attempt.domain;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptTest {

    private final Problem problem = new Problem(1L, "제목", "명세", List.of(
            new ProblemFile("src/Main.java", "class Main {}")
    ));

    @Test
    void 문제로부터_시작하면_스켈레톤_파일과_빈_턴_목록을_가진다() {
        Attempt attempt = Attempt.start(problem);

        assertThat(attempt.id()).isNull();
        assertThat(attempt.problemId()).isEqualTo(1L);
        assertThat(attempt.currentFiles()).containsExactly(new ProblemFile("src/Main.java", "class Main {}"));
        assertThat(attempt.turns()).isEmpty();
    }

    @Test
    void 턴을_적용하면_현재_파일이_생성_결과로_교체된다() {
        Attempt attempt = Attempt.start(problem);
        GeneratedCode generated = new GeneratedCode(
                List.of(new ProblemFile("src/Main.java", "class Main { void run() {} }")),
                "메서드를 추가했습니다."
        );

        Attempt applied = attempt.applyTurn("메서드 추가해줘", generated);

        assertThat(applied.currentFiles())
                .containsExactly(new ProblemFile("src/Main.java", "class Main { void run() {} }"));
        assertThat(applied.turns()).hasSize(1);
        assertThat(applied.turns().getFirst().userPrompt()).isEqualTo("메서드 추가해줘");
        assertThat(applied.turns().getFirst().aiSummary()).isEqualTo("메서드를 추가했습니다.");
    }

    @Test
    void 턴을_적용하면_이전_파일_대비_변경_목록을_턴에_기록한다() {
        Attempt attempt = Attempt.start(problem);
        GeneratedCode generated = new GeneratedCode(
                List.of(
                        new ProblemFile("src/Main.java", "class Main { void run() {} }"),
                        new ProblemFile("src/Util.java", "class Util {}")
                ),
                "요약"
        );

        Attempt applied = attempt.applyTurn("요청", generated);

        assertThat(applied.turns().getFirst().changes()).containsExactly(
                new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED),
                new FileChange("src/Util.java", FileChange.ChangeType.ADDED)
        );
    }

    @Test
    void 턴을_적용하면_직전_턴의_결과를_기준으로_변경_목록을_계산한다() {
        Attempt attempt = Attempt.start(problem)
                .applyTurn("첫 요청", new GeneratedCode(
                        List.of(new ProblemFile("src/Main.java", "class Main { void run() {} }")),
                        "첫 요약"
                ));

        Attempt applied = attempt.applyTurn("두 번째 요청", new GeneratedCode(
                List.of(new ProblemFile("src/Main.java", "class Main { void run() {} void stop() {} }")),
                "두 번째 요약"
        ));

        assertThat(applied.turns()).hasSize(2);
        assertThat(applied.turns().get(1).changes())
                .containsExactly(new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED));
    }

    @Test
    void 턴을_적용해도_기존_어템프트는_변하지_않는다() {
        Attempt attempt = Attempt.start(problem);

        attempt.applyTurn("요청", new GeneratedCode(
                List.of(new ProblemFile("src/Main.java", "변경된 내용")),
                "요약"
        ));

        assertThat(attempt.turns()).isEmpty();
        assertThat(attempt.currentFiles()).containsExactly(new ProblemFile("src/Main.java", "class Main {}"));
    }
}
