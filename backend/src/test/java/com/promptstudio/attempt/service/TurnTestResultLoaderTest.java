package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.CodeRunCase;
import com.promptstudio.attempt.domain.CodeRunCaseStatus;
import com.promptstudio.attempt.domain.CodeRunCaseTally;
import com.promptstudio.attempt.domain.CodeRunResult;
import com.promptstudio.attempt.domain.CodeRunStatus;
import com.promptstudio.attempt.domain.CodeRunSummary;
import com.promptstudio.attempt.domain.CodeRunView;
import com.promptstudio.attempt.domain.TurnTestResults;
import com.promptstudio.attempt.domain.TurnTestResults.TurnTestResult;
import com.promptstudio.attempt.repository.CodeRunRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TurnTestResultLoaderTest {

    private final InMemoryCodeRuns codeRunRepository = new InMemoryCodeRuns();
    private final TurnTestResultLoader loader = new TurnTestResultLoader(codeRunRepository);

    @Test
    void 실행_기록이_없으면_비어_있다() {
        assertThat(loader.load(1L, 3).isEmpty()).isTrue();
    }

    /**
     * 사용자는 같은 턴에서 여러 번 돌릴 수 있다. 화면이 보여주는 것은 가장 최근 결과이므로 피드백도 같다.
     */
    @Test
    void 턴마다_가장_최근_종료_실행을_고른다() {
        codeRunRepository.add(run(0, CodeRunStatus.TEST_FAILED, 1), tally(5, 1), List.of());
        codeRunRepository.add(run(0, CodeRunStatus.SUCCEEDED, 2), tally(5, 5), List.of());

        TurnTestResults results = loader.load(1L, 1);

        assertThat(results.forTurn(0))
                .extracting(TurnTestResult::status, TurnTestResult::passed)
                .containsExactly(CodeRunStatus.SUCCEEDED, 5);
    }

    /**
     * 진행 중인 실행에는 결과가 없다. 그것을 고르면 통과 0건으로 읽혀 델타가 거짓말을 한다.
     */
    @Test
    void 진행_중인_실행은_고르지_않는다() {
        codeRunRepository.add(run(0, CodeRunStatus.TEST_FAILED, 1), tally(5, 3), List.of());
        codeRunRepository.add(run(0, CodeRunStatus.QUEUED, 2), null, List.of());

        assertThat(loader.load(1L, 1).forTurn(0).passed()).isEqualTo(3);
    }

    /**
     * turn_ordinal이 null인 행은 턴이 없는 시점의 실행, 곧 시작 스켈레톤이다.
     */
    @Test
    void turn_ordinal이_null인_실행은_베이스라인이다() {
        codeRunRepository.add(run(null, CodeRunStatus.TEST_FAILED, 1), tally(4, 1), List.of());
        codeRunRepository.add(run(0, CodeRunStatus.TEST_FAILED, 2), tally(4, 3), List.of());

        TurnTestResults results = loader.load(1L, 1);

        assertThat(results.baselinePassed()).isEqualTo(1);
        assertThat(results.baselineTotal()).isEqualTo(4);
        assertThat(results.forTurn(0).delta()).isEqualTo(2);
    }

    @Test
    void 실패한_테스트_이름만_싣는다() {
        codeRunRepository.add(
                run(0, CodeRunStatus.TEST_FAILED, 1),
                new CodeRunCaseTally(4, 1, 1, 1, 1),
                List.of(
                        testCase("취소하면_상태가_CANCELED가_된다", CodeRunCaseStatus.PASSED),
                        testCase("배송_시작된_주문은_취소할_수_없다", CodeRunCaseStatus.FAILED),
                        testCase("취소하면_재고가_복구된다", CodeRunCaseStatus.ERROR),
                        testCase("이미_취소된_주문은_아무_일도_없다", CodeRunCaseStatus.SKIPPED)));

        assertThat(loader.load(1L, 1).forTurn(0).failedTestNames())
                .containsExactly("배송_시작된_주문은_취소할_수_없다", "취소하면_재고가_복구된다");
    }

    /**
     * 턴이 지워진 뒤 남은 실행 행이 프롬프트의 없는 턴을 가리키면 안 된다.
     */
    @Test
    void 턴_수를_벗어난_실행은_버린다() {
        codeRunRepository.add(run(0, CodeRunStatus.SUCCEEDED, 1), tally(3, 3), List.of());
        codeRunRepository.add(run(5, CodeRunStatus.SUCCEEDED, 2), tally(3, 3), List.of());

        assertThat(loader.load(1L, 1).turns()).extracting(TurnTestResult::turnOrdinal).containsExactly(0);
    }

    @Test
    void 실행이_있는_턴만_턴_순서대로_담는다() {
        codeRunRepository.add(run(2, CodeRunStatus.SUCCEEDED, 1), tally(3, 3), List.of());
        codeRunRepository.add(run(0, CodeRunStatus.TEST_FAILED, 2), tally(3, 1), List.of());

        assertThat(loader.load(1L, 3).turns())
                .extracting(TurnTestResult::turnOrdinal)
                .containsExactly(0, 2);
    }

    private CodeRunSummary run(Integer turnOrdinal, CodeRunStatus status, int createdAtSeconds) {
        return new CodeRunSummary(
                UUID.randomUUID(),
                turnOrdinal,
                status,
                null,
                null,
                Instant.ofEpochSecond(createdAtSeconds),
                null);
    }

    private CodeRunCaseTally tally(int total, int passed) {
        return new CodeRunCaseTally(total, passed, total - passed, 0, 0);
    }

    private CodeRunCase testCase(String name, CodeRunCaseStatus status) {
        return new CodeRunCase("com.shop.OrderServiceTest", name, status, null, null);
    }

    /**
     * 선택 규칙만 보기 위한 인메모리 저장소. 실제 저장소와 같이 최근순으로 돌려준다.
     *
     * <p>이름이 Repository로 끝나지 않는 것은 {@code PackagingConventionTest}가 그 이름을 영속성 타입으로
     * 보고 {@code <모듈>.repository} 패키지에 있으라고 요구하기 때문이다. 이건 테스트 페이크다.
     */
    private static final class InMemoryCodeRuns implements CodeRunRepository {

        private final List<CodeRunSummary> runs = new ArrayList<>();
        private final Map<UUID, CodeRunCaseTally> tallies = new HashMap<>();
        private final Map<UUID, List<CodeRunCase>> cases = new HashMap<>();

        private void add(CodeRunSummary run, CodeRunCaseTally tally, List<CodeRunCase> runCases) {
            runs.add(run);

            if (tally != null) {
                tallies.put(run.id(), tally);
            }

            cases.put(run.id(), runCases);
        }

        @Override
        public List<CodeRunSummary> findAllByAttemptId(Long attemptId) {
            return runs.stream()
                    .sorted(Comparator.comparing(CodeRunSummary::createdAt).reversed())
                    .toList();
        }

        @Override
        public Map<UUID, CodeRunCaseTally> tallyCasesByRunIds(Collection<UUID> runIds) {
            Map<UUID, CodeRunCaseTally> found = new HashMap<>();

            for (UUID runId : runIds) {
                CodeRunCaseTally tally = tallies.get(runId);

                if (tally != null) {
                    found.put(runId, tally);
                }
            }

            return found;
        }

        @Override
        public List<CodeRunCase> findCasesByRunId(UUID runId) {
            return cases.getOrDefault(runId, List.of());
        }

        @Override
        public boolean tryInsertQueued(UUID runId, Long attemptId, Integer turnOrdinal, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CodeRunView> findByIdAndAttemptId(UUID runId, Long attemptId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int expireStale(Instant staleBefore, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int applyResult(CodeRunResult result, Instant finishedAt) {
            throw new UnsupportedOperationException();
        }
    }
}
