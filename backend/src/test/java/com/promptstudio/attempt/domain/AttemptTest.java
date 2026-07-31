package com.promptstudio.attempt.domain;

import com.promptstudio.attempt.exception.AttemptAlreadySubmittedException;
import com.promptstudio.attempt.exception.FeedbackTurnCountMismatchException;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptTest {

    private final Problem problem = new Problem(1L, "hello-world", "제목", "명세", List.of(
            new ProblemFile("src/Main.java", "class Main {}")
    ));

    private final GeneratedCode generated = new GeneratedCode(
            List.of(new ProblemFile("src/Main.java", "class Main { void run() {} }")),
            "요약",
            List.of()
    );

    @Test
    void 문제로부터_시작하면_스켈레톤_파일과_빈_턴_목록을_가진다() {
        Attempt attempt = Attempt.start(problem, 1L);

        assertThat(attempt.id()).isNull();
        assertThat(attempt.problemId()).isEqualTo(1L);
        assertThat(attempt.currentFiles()).containsExactly(new ProblemFile("src/Main.java", "class Main {}"));
        assertThat(attempt.turns()).isEmpty();
    }

    @Test
    void 여러_턴을_적용해도_시작_스켈레톤은_그대로_남는다() {
        Attempt attempt = Attempt.start(problem, 1L);
        attempt.applyTurn("첫 요청", generated);
        attempt.applyTurn("두 번째 요청", new GeneratedCode(
                List.of(new ProblemFile("src/Util.java", "class Util {}")),
                "요약"
        , List.of()
        ));

        assertThat(attempt.baseFiles()).containsExactly(new ProblemFile("src/Main.java", "class Main {}"));
    }

    @Test
    void 현재_파일은_스켈레톤에_턴별_변경을_재생한_결과다() {
        Attempt attempt = Attempt.start(problem, 1L);
        attempt.applyTurn("Util을 추가해줘", new GeneratedCode(
                List.of(
                        new ProblemFile("src/Main.java", "class Main { void run() {} }"),
                        new ProblemFile("src/Util.java", "class Util {}")
                ),
                "첫 요약"
        , List.of()
        ));

        attempt.applyTurn("Main은 지우고 Util만 고쳐줘", new GeneratedCode(
                List.of(new ProblemFile("src/Util.java", "class Util { void help() {} }")),
                "두 번째 요약"
        , List.of()
        ));

        assertThat(attempt.currentFiles())
                .containsExactly(new ProblemFile("src/Util.java", "class Util { void help() {} }"));
    }

    @Test
    void 삭제한_파일을_다시_추가하면_현재_파일에_되살아난다() {
        Attempt attempt = Attempt.start(problem, 1L);
        attempt.applyTurn("Main을 지워줘", new GeneratedCode(
                List.of(new ProblemFile("src/Util.java", "class Util {}")),
                "첫 요약"
        , List.of()
        ));

        attempt.applyTurn("Main을 되살려줘", new GeneratedCode(
                List.of(
                        new ProblemFile("src/Util.java", "class Util {}"),
                        new ProblemFile("src/Main.java", "class Main { void run() {} }")
                ),
                "두 번째 요약"
        , List.of()
        ));

        assertThat(attempt.currentFiles()).containsExactly(
                new ProblemFile("src/Util.java", "class Util {}"),
                new ProblemFile("src/Main.java", "class Main { void run() {} }")
        );
    }

    @Test
    void 시작하면_상태는_IN_PROGRESS이고_피드백은_없다() {
        Attempt attempt = Attempt.start(problem, 1L);

        assertThat(attempt.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(attempt.feedback()).isNull();
    }

    @Test
    void 턴을_적용하면_현재_파일이_생성_결과로_교체된다() {
        Attempt attempt = Attempt.start(problem, 1L);

        attempt.applyTurn("메서드 추가해줘", new GeneratedCode(
                List.of(new ProblemFile("src/Main.java", "class Main { void run() {} }")),
                "메서드를 추가했습니다.",
                List.of()
        ));

        assertThat(attempt.currentFiles())
                .containsExactly(new ProblemFile("src/Main.java", "class Main { void run() {} }"));
        assertThat(attempt.turns()).hasSize(1);
        assertThat(attempt.turns().getFirst().userPrompt()).isEqualTo("메서드 추가해줘");
        assertThat(attempt.turns().getFirst().aiSummary()).isEqualTo("메서드를 추가했습니다.");
    }

    @Test
    void 제출하면_상태가_SUBMITTED로_바뀌고_피드백이_저장된다() {
        Attempt attempt = Attempt.start(problem, 1L);

        attempt.submit(new AttemptFeedback(List.of(), "피드백 내용"));

        assertThat(attempt.status()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(attempt.feedback()).isEqualTo("피드백 내용");
    }

    @Test
    void 제출하면_턴별_피드백을_순서대로_배정한다() {
        Attempt attempt = Attempt.start(problem, 1L);
        attempt.applyTurn("첫 요청", generated);
        attempt.applyTurn("두 번째 요청", generated);

        attempt.submit(new AttemptFeedback(List.of("첫 턴 피드백", "두 번째 턴 피드백"), "전체 피드백"));

        assertThat(attempt.turns().get(0).feedback()).isEqualTo("첫 턴 피드백");
        assertThat(attempt.turns().get(1).feedback()).isEqualTo("두 번째 턴 피드백");
        assertThat(attempt.feedback()).isEqualTo("전체 피드백");
    }

    @Test
    void 턴_피드백_개수가_턴_수와_다르면_제출하지_않는다() {
        Attempt attempt = Attempt.start(problem, 1L);
        attempt.applyTurn("첫 요청", generated);

        assertThatThrownBy(() -> attempt.submit(new AttemptFeedback(List.of("첫 턴", "둘째 턴"), "전체 피드백")))
                .isInstanceOf(FeedbackTurnCountMismatchException.class)
                .hasMessage("어템프트의 턴 수(1)와 턴 피드백 개수(2)가 다릅니다.");
        assertThat(attempt.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(attempt.turns().getFirst().feedback()).isNull();
    }

    @Test
    void 제출된_어템프트에_턴을_적용하면_예외를_던진다() {
        Attempt attempt = Attempt.start(problem, 1L);
        attempt.submit(new AttemptFeedback(List.of(), "피드백 내용"));

        assertThatThrownBy(() -> attempt.applyTurn("추가 요청", generated))
                .isInstanceOf(AttemptAlreadySubmittedException.class);
    }

    @Test
    void 이미_제출된_어템프트를_다시_제출하면_예외를_던진다() {
        Attempt attempt = Attempt.start(problem, 1L);
        attempt.submit(new AttemptFeedback(List.of(), "피드백 내용"));

        assertThatThrownBy(() -> attempt.submit(new AttemptFeedback(List.of(), "다른 피드백")))
                .isInstanceOf(AttemptAlreadySubmittedException.class);
    }

    @Test
    void 턴을_적용하면_이전_파일_대비_변경_목록을_턴에_기록한다() {
        Attempt attempt = Attempt.start(problem, 1L);

        attempt.applyTurn("요청", new GeneratedCode(
                List.of(
                        new ProblemFile("src/Main.java", "class Main { void run() {} }"),
                        new ProblemFile("src/Util.java", "class Util {}")
                ),
                "요약",
                List.of()
        ));

        assertThat(attempt.turns().getFirst().changes()).containsExactly(
                new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED, "class Main { void run() {} }"),
                new FileChange("src/Util.java", FileChange.ChangeType.ADDED, "class Util {}")
        );
    }

    @Test
    void 턴을_적용하면_툴콜_트레이스를_턴에_기록한다() {
        Attempt attempt = Attempt.start(problem, 1L);

        attempt.applyTurn("요청", new GeneratedCode(
                List.of(new ProblemFile("src/Main.java", "class Main { void run() {} }")),
                "요약",
                List.of(new ToolCallEntry("list_files", null), new ToolCallEntry("edit_file", "src/Main.java"))
        ));

        assertThat(attempt.turns().getFirst().toolCalls()).containsExactly(
                new ToolCallEntry("list_files", null),
                new ToolCallEntry("edit_file", "src/Main.java")
        );
    }

    @Test
    void 턴을_적용하면_직전_턴의_결과를_기준으로_변경_목록을_계산한다() {
        Attempt attempt = Attempt.start(problem, 1L);
        attempt.applyTurn("첫 요청", new GeneratedCode(
                List.of(new ProblemFile("src/Main.java", "class Main { void run() {} }")),
                "첫 요약",
                List.of()
        ));

        attempt.applyTurn("두 번째 요청", new GeneratedCode(
                List.of(new ProblemFile("src/Main.java", "class Main { void run() {} void stop() {} }")),
                "두 번째 요약",
                List.of()
        ));

        assertThat(attempt.turns()).hasSize(2);
        assertThat(attempt.turns().get(1).changes()).containsExactly(
                new FileChange("src/Main.java", FileChange.ChangeType.MODIFIED, "class Main { void run() {} void stop() {} }")
        );
    }
}
