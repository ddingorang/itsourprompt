package com.promptstudio.attempt.controller;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.domain.CodeRunView;
import com.promptstudio.attempt.service.AttemptService;
import com.promptstudio.attempt.service.CodeRunService;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import com.promptstudio.support.FakeCodeRunConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제출 후 지시 파일에 붙여넣을 규칙 한 줄이 피드백 응답에 실린다.
 *
 * <p>이 값은 저장하지 않고 읽을 때마다 계산한다. 그래서 규칙이 생기기 전에 제출된 어템프트도 열면
 * 바로 받는다 — 마이그레이션이 없다.
 */
@AutoConfigureMockMvc
@Import({FakeAiConfiguration.class, FakeCodeRunConfiguration.class, AttemptApiAuthenticationConfiguration.class})
class CarryLineApiTest extends DatabaseTest {

    private static final String RULE =
            "무엇을 바꿨다고만 말하지 마라. "
                    + "그 변경이 실제로 동작하는지 사람이 확인할 방법을 요약에 반드시 함께 적어라.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AttemptService attemptService;

    @Autowired
    private CodeRunService codeRunService;

    @Autowired
    private ProblemRepository problemRepository;

    @Test
    void 실행하지_않고_제출하면_규칙_한_줄이_실린다() throws Exception {
        Long attemptId = submittedAttempt(2);

        mockMvc.perform(get("/api/attempts/{id}/feedback", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carry.signal").value("turn_has_no_finished_run"))
                .andExpect(jsonPath("$.carry.rule").value(RULE))
                .andExpect(jsonPath("$.carry.reason").value("이번 세션의 2턴에서 한 번도 코드를 실행해 결과를 "
                        + "확인하지 않으셨어요. AI가 확인 방법을 요약에 함께 적어 두면 다음엔 무엇을 돌려 봐야 할지 "
                        + "바로 알 수 있어요."));
    }

    @Test
    void 일부_턴만_실행했으면_안_돌린_턴만_센다() throws Exception {
        Long attemptId = startedAttempt(2);
        finishRun(attemptId, 0, CodeRunStatus.SUCCEEDED);
        attemptService.submit(attemptId, owner());

        mockMvc.perform(get("/api/attempts/{id}/feedback", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carry.reason").value(startsWith("2턴 중 1턴에서")));
    }

    /**
     * 걸리는 습관이 없으면 화면에 아무것도 올리지 않는다 — 빈 절을 세우지 않는다.
     *
     * <p>프롬프트가 파일을 이름으로 짚는다. S1이 생기기 전에는 아무 프롬프트여도 됐지만, 이제
     * 가짜 생성기가 매 턴 {@code Main.java}를 고치므로 짚지 않으면 S1이 켜져 무신호 케이스가
     * 사라진다. <b>의도된 행동 변화다</b> — 그 발화를 아래 테스트가 따로 잡는다.
     */
    @Test
    void 모든_턴을_확인했으면_줄이_실리지_않는다() throws Exception {
        Long attemptId = startedAttempt(2, "Main.java에 Hello 출력해줘");
        finishRun(attemptId, 0, CodeRunStatus.SUCCEEDED);
        finishRun(attemptId, 1, CodeRunStatus.TEST_FAILED);
        attemptService.submit(attemptId, owner());

        mockMvc.perform(get("/api/attempts/{id}/feedback", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallMd").value("생성된 피드백"))
                .andExpect(jsonPath("$.carry").value(nullValue()));
    }

    /**
     * S5가 꺼져야 뒤 순위 신호가 드러난다. 두 턴을 다 실행해 놓고 프롬프트만 파일을 안 짚게 둔다.
     */
    @Test
    void 실행은_다_했지만_앞_턴이_바꾼_파일을_안_짚으면_S1_줄이_실린다() throws Exception {
        Long attemptId = startedAttempt(2);
        finishRun(attemptId, 0, CodeRunStatus.SUCCEEDED);
        finishRun(attemptId, 1, CodeRunStatus.SUCCEEDED);
        attemptService.submit(attemptId, owner());

        mockMvc.perform(get("/api/attempts/{id}/feedback", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carry.signal").value("prompt_names_previous_changed_file"))
                .andExpect(jsonPath("$.carry.rule").value("작업 요약에는 이번에 바꾼 모든 파일의 이름이 "
                        + "반드시 들어가야 한다. 파일 이름 없이 변경 내용을 보고하지 마라."))
                .andExpect(jsonPath("$.carry.reason").value(startsWith("2턴 중 1턴에서 앞 턴이 바꾼 파일을")));
    }

    /**
     * 제출 응답과 피드백 조회는 같은 봉투를 쓴다. 제출 직후 화면이 다시 부르지 않아도 줄을 보여 준다.
     */
    @Test
    void 제출_응답에도_같은_줄이_실린다() throws Exception {
        Long attemptId = startedAttempt(1);

        mockMvc.perform(post("/api/attempts/{id}/submit", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carry.rule").value(RULE));
    }

    /**
     * 제출된 어템프트는 누구나 읽는다. 규칙 줄도 같은 봉투에 실리므로 같은 범위로 나간다.
     */
    @Test
    void 제출_전에는_피드백_자체를_읽을_수_없다() throws Exception {
        Long attemptId = startedAttempt(1);

        mockMvc.perform(get("/api/attempts/{id}/feedback", attemptId))
                .andExpect(status().isNotFound());
    }

    private void finishRun(Long attemptId, int turnOrdinal, CodeRunStatus status) {
        CodeRunView run = codeRunService.requestRun(attemptId, owner(), turnOrdinal);

        codeRunService.applyResult(new CodeRunResult(run.id(), status, 0, "", "", 900L));
    }

    private Long submittedAttempt(int turns) {
        Long attemptId = startedAttempt(turns);

        attemptService.submit(attemptId, owner());

        return attemptId;
    }

    private Long startedAttempt(int turns) {
        return startedAttempt(turns, "Hello 출력해줘");
    }

    private Long startedAttempt(int turns, String userPrompt) {
        AttemptView attempt = attemptService.startAttempt(newProblem().id(), owner(), null);

        for (int turn = 0; turn < turns; turn++) {
            attemptService.addTurn(attempt.id(), owner(), userPrompt, null);
        }

        return attempt.id();
    }

    private AttemptOwner owner() {
        return AttemptOwner.user(ownerId);
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem(
                "carry-line-" + UUID.randomUUID(),
                "Hello World 출력",
                "# 명세",
                List.of(new ProblemFile("src/main/java/Main.java", "class Main {}")),
                List.of()
        ));
    }
}
