package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptLlmCall;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.LlmCallPurpose;
import com.promptstudio.attempt.domain.LlmCallStatus;
import com.promptstudio.attempt.port.CodeGenerationException;
import com.promptstudio.attempt.port.FeedbackGenerationException;
import com.promptstudio.attempt.repository.LlmCallRepository;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 호출 사용량이 어느 시점에 어떤 행으로 남는지 확인한다. 실패해도 이미 쓴 토큰은 기록에 남아야 한다.
 */
@Import(FakeAiConfiguration.class)
class LlmCallRecordingTest extends DatabaseTest {

    private static final ProblemFile SKELETON = new ProblemFile("src/main/java/Main.java", "class Main {}");

    @Autowired
    private AttemptService attemptService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private LlmCallRepository llmCallRepository;

    @Autowired
    private FakeAiConfiguration.FakeCodeGenerator codeGenerator;

    @Autowired
    private FakeAiConfiguration.FakeFeedbackGenerator feedbackGenerator;

    @Test
    void 턴_성공_시_호출_행이_같은_트랜잭션에_저장된다() {
        AttemptView started = attemptService.startAttempt(newProblem().id(), ownerId, null);

        attemptService.addTurn(started.id(), ownerId, "Hello 출력해줘");

        List<AttemptLlmCall> calls = llmCallRepository.findByAttemptId(started.id());
        assertThat(calls).hasSize(2);
        assertThat(calls)
                .extracting(
                        AttemptLlmCall::turnOrdinal,
                        AttemptLlmCall::purpose,
                        AttemptLlmCall::seq,
                        AttemptLlmCall::status,
                        AttemptLlmCall::model
                )
                .containsExactlyInAnyOrder(
                        tuple(0, LlmCallPurpose.CODE, 1, LlmCallStatus.SUCCESS, "test-model"),
                        tuple(0, LlmCallPurpose.CODE, 2, LlmCallStatus.SUCCESS, "test-model")
                );

        AttemptLlmCall first = callWithSeq(calls, 1);
        assertThat(first.inputTokens()).isEqualTo(1_000L);
        assertThat(first.outputTokens()).isEqualTo(200L);
        assertThat(first.cachedInputTokens()).isEqualTo(400L);
        assertThat(first.reasoningTokens()).isEqualTo(50L);
        assertThat(first.latencyMs()).isEqualTo(120L);
        assertThat(first.errorType()).isNull();
        assertThat(first.createdAt()).isNotNull();
        // (1000 - 400) * 1.0 + 400 * 0.5 + 200 * 2.0 = 1200 / 1_000_000
        assertThat(first.cost()).isEqualByComparingTo("0.00120000");
        // (1500 - 600) * 1.0 + 600 * 0.5 + 300 * 2.0 = 1800 / 1_000_000
        assertThat(callWithSeq(calls, 2).cost()).isEqualByComparingTo("0.00180000");
    }

    @Test
    void 생성_실패_시_누적분을_flush하고_FAILED_행을_남긴다() {
        AttemptView started = attemptService.startAttempt(newProblem().id(), ownerId, null);
        codeGenerator.failNextWith(new CodeGenerationException(
                "AI 코드 생성 요청에 실패했습니다.",
                null,
                "provider-error",
                FakeAiConfiguration.CODE_GENERATION_USAGE
        ));

        assertThatThrownBy(() -> attemptService.addTurn(started.id(), ownerId, "Hello 출력해줘"))
                .isInstanceOf(CodeGenerationException.class);

        List<AttemptLlmCall> calls = llmCallRepository.findByAttemptId(started.id());
        assertThat(calls).hasSize(3);
        assertThat(calls)
                .extracting(
                        AttemptLlmCall::turnOrdinal,
                        AttemptLlmCall::purpose,
                        AttemptLlmCall::seq,
                        AttemptLlmCall::status,
                        AttemptLlmCall::errorType
                )
                .containsExactlyInAnyOrder(
                        tuple(null, LlmCallPurpose.CODE, 1, LlmCallStatus.SUCCESS, null),
                        tuple(null, LlmCallPurpose.CODE, 2, LlmCallStatus.SUCCESS, null),
                        tuple(null, LlmCallPurpose.CODE, 3, LlmCallStatus.FAILED, "provider-error")
                );

        AttemptLlmCall marker = callWithSeq(calls, 3);
        assertThat(marker.model()).isNull();
        assertThat(marker.inputTokens()).isNull();
        assertThat(marker.cost()).isNull();
        assertThat(attemptService.getAttempt(started.id(), ownerId).turns()).isEmpty();
    }

    @Test
    void 제출_시_피드백_호출_행이_저장된다() {
        AttemptView started = attemptService.startAttempt(newProblem().id(), ownerId, null);
        attemptService.addTurn(started.id(), ownerId, "Hello 출력해줘");

        attemptService.submit(started.id(), ownerId);

        List<AttemptLlmCall> feedbackCalls = purpose(started.id(), LlmCallPurpose.FEEDBACK);
        assertThat(feedbackCalls).hasSize(1);

        AttemptLlmCall call = feedbackCalls.getFirst();
        assertThat(call.turnOrdinal()).isNull();
        assertThat(call.seq()).isEqualTo(1);
        assertThat(call.status()).isEqualTo(LlmCallStatus.SUCCESS);
        assertThat(call.inputTokens()).isEqualTo(2_000L);
        assertThat(call.outputTokens()).isEqualTo(400L);
        // 2000 * 1.0 + 400 * 2.0 = 2800 / 1_000_000
        assertThat(call.cost()).isEqualByComparingTo("0.00280000");
    }

    @Test
    void 피드백_실패_시_누적분을_flush하고_FAILED_행을_남긴다() {
        AttemptView started = attemptService.startAttempt(newProblem().id(), ownerId, null);
        attemptService.addTurn(started.id(), ownerId, "Hello 출력해줘");
        feedbackGenerator.failNextWith(new FeedbackGenerationException(
                "invalid-json",
                "AI feedback response was not valid JSON.",
                null,
                FakeAiConfiguration.FEEDBACK_USAGE
        ));

        assertThatThrownBy(() -> attemptService.submit(started.id(), ownerId))
                .isInstanceOf(FeedbackGenerationException.class);

        assertThat(purpose(started.id(), LlmCallPurpose.FEEDBACK))
                .extracting(AttemptLlmCall::seq, AttemptLlmCall::status, AttemptLlmCall::errorType)
                .containsExactlyInAnyOrder(
                        tuple(1, LlmCallStatus.SUCCESS, null),
                        tuple(2, LlmCallStatus.FAILED, "invalid-json")
                );
        assertThat(attemptService.getAttempt(started.id(), ownerId).status()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    private List<AttemptLlmCall> purpose(Long attemptId, LlmCallPurpose purpose) {
        return llmCallRepository.findByAttemptId(attemptId).stream()
                .filter(call -> call.purpose() == purpose)
                .toList();
    }

    private AttemptLlmCall callWithSeq(List<AttemptLlmCall> calls, int seq) {
        return calls.stream()
                .filter(call -> call.seq() == seq)
                .findFirst()
                .orElseThrow();
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem("hello-world", "제목", "명세", List.of(SKELETON)));
    }
}
