package com.promptstudio.ranking.repository;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.service.AttemptService;
import com.promptstudio.guest.repository.GuestAttemptOwnershipRepository;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.ranking.domain.RankedPage;
import com.promptstudio.ranking.domain.RankingEntry;
import com.promptstudio.support.DatabaseTest;
import com.promptstudio.support.FakeAiConfiguration;
import com.promptstudio.user.domain.User;
import com.promptstudio.user.repository.UserRepository;
import org.jooq.DSLContext;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * 랭킹의 자격 판정과 비용 계산.
 *
 * <p>테스트 데이터는 운영 경로(AttemptService)를 그대로 타서 만든다. FakeAiConfiguration이 턴마다
 * test-model 호출 2건(1000/200/캐시 400, 1500/300/캐시 600)을 남기고, 테스트 단가(1.0 / 0.5 / 2.0)로
 * 계산하면 턴 하나당 0.00300000이다. 워커가 없어 code_run만 직접 넣는다.
 */
@Import(FakeAiConfiguration.class)
class RankingQueryRepositoryTest extends DatabaseTest {

    private static final String TURN_COST = "0.00300000";

    @Autowired
    private RankingQueryRepository rankingQueryRepository;

    @Autowired
    private AttemptService attemptService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuestAttemptOwnershipRepository guestAttemptOwnershipRepository;

    @Autowired
    private DSLContext dsl;

    @Test
    void 마지막_턴이_채점을_통과한_제출만_랭킹에_든다() {
        Problem problem = newProblem();
        Long passed = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        Long notRun = submittedAttempt(problem, AttemptOwner.user(otherUserId()), 1);
        deleteRuns(notRun);

        RankedPage page = rankingQueryRepository.findTop(problem.id(), 10);

        assertThat(page.entries()).extracting(RankingEntry::attemptId).containsExactly(passed);
        assertThat(page.totalCount()).isEqualTo(1);
    }

    @Test
    void 제출하지_않은_풀이는_랭킹에_들지_않는다() {
        Problem problem = newProblem();
        AttemptOwner owner = AttemptOwner.user(ownerId);
        AttemptView attempt = attemptService.startAttempt(problem.id(), owner, null);
        attemptService.addTurn(attempt.id(), owner, "Hello 출력해줘", null);
        succeedRun(attempt.id(), 0);

        RankedPage page = rankingQueryRepository.findTop(problem.id(), 10);

        assertThat(page.entries()).isEmpty();
        assertThat(page.totalCount()).isZero();
    }

    @Test
    void 마지막_턴이_아닌_턴만_통과했으면_랭킹에_들지_않는다() {
        Problem problem = newProblem();
        Long attemptId = submittedAttempt(problem, AttemptOwner.user(ownerId), 2);
        deleteRuns(attemptId);
        succeedRun(attemptId, 0);

        assertThat(top(problem.id(), 10)).isEmpty();
    }

    @Test
    void 채점이_깨진_실행만_있으면_랭킹에_들지_않는다() {
        Problem problem = newProblem();
        Long attemptId = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        deleteRuns(attemptId);
        insertRun(attemptId, 0, "TEST_FAILED");

        assertThat(top(problem.id(), 10)).isEmpty();
    }

    @Test
    void 턴에_속한_호출_기록이_없으면_랭킹에_들지_않는다() {
        // 사용량 기록 도입 이전 어템프트다. 비용이 0인 게 아니라 모르는 것이므로 표에서 뺀다.
        Problem problem = newProblem();
        Long attemptId = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        dsl.execute("DELETE FROM attempt_llm_call WHERE attempt_id = ?", attemptId);

        assertThat(top(problem.id(), 10)).isEmpty();
    }

    @Test
    void 토큰을_모르는_호출이_하나라도_있으면_랭킹에_들지_않는다() {
        Problem problem = newProblem();
        Long attemptId = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        dsl.execute("UPDATE attempt_llm_call SET input_tokens = NULL WHERE attempt_id = ? AND seq = 1", attemptId);

        assertThat(top(problem.id(), 10)).isEmpty();
    }

    @Test
    void 단가를_모르는_모델을_쓴_호출이_있으면_랭킹에_들지_않는다() {
        Problem problem = newProblem();
        Long attemptId = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        dsl.execute("UPDATE attempt_llm_call SET model = 'unpriced-model' WHERE attempt_id = ? AND seq = 2",
                attemptId);

        assertThat(top(problem.id(), 10)).isEmpty();
    }

    @Test
    void 비용은_현재_단가로_다시_잰_값이다() {
        Problem problem = newProblem();
        Long attemptId = submittedAttempt(problem, AttemptOwner.user(ownerId), 2);

        RankingEntry entry = top(problem.id(), 10).getFirst();

        // 턴 2개 = 0.00300000 * 2. 피드백 호출(0.00280000)은 턴에 속하지 않아 빠진다.
        assertThat(entry.cost()).isEqualByComparingTo("0.00600000");
        assertThat(entry.cost().scale()).isEqualTo(8);
        assertThat(entry.attemptId()).isEqualTo(attemptId);
        assertThat(entry.rank()).isEqualTo(1);
        assertThat(entry.turns()).isEqualTo(2);
        assertThat(entry.rounds()).isEqualTo(4);
        assertThat(entry.uncachedInputTokens()).isEqualTo(3_000L);
        assertThat(entry.cachedInputTokens()).isEqualTo(2_000L);
        assertThat(entry.outputTokens()).isEqualTo(1_000L);
        assertThat(entry.userId()).isEqualTo(ownerId);
        assertThat(entry.nickname()).isEqualTo("test owner");
        assertThat(entry.submittedAt()).isNotNull();
    }

    @Test
    void 저장된_비용이_아니라_지금_단가로_잰다() {
        Problem problem = newProblem();
        submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        // 쓰기 시점 단가로 박힌 지출 기록을 흔들어도 랭킹은 흔들리지 않아야 한다.
        dsl.execute("UPDATE attempt_llm_call SET cost = 99.0");

        assertThat(top(problem.id(), 10).getFirst().cost())
                .isEqualByComparingTo(TURN_COST);
    }

    @Test
    void 동점은_같은_등수를_받고_다음_등수는_건너뛴다() {
        Problem problem = newProblem();
        Long first = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        Long second = submittedAttempt(problem, AttemptOwner.user(otherUserId()), 1);
        Long third = submittedAttempt(problem, AttemptOwner.user(ownerId), 2);
        setSubmittedAt(first, "2026-08-01T00:00:00Z");
        setSubmittedAt(second, "2026-08-02T00:00:00Z");
        setSubmittedAt(third, "2026-08-03T00:00:00Z");

        List<RankingEntry> entries = top(problem.id(), 10);

        assertThat(entries).extracting(RankingEntry::attemptId).containsExactly(first, second, third);
        assertThat(entries).extracting(RankingEntry::rank).containsExactly(1, 1, 3);
    }

    @Test
    void 동점이면_먼저_제출한_쪽이_앞에_온다() {
        Problem problem = newProblem();
        Long earlier = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        Long later = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        setSubmittedAt(earlier, "2026-08-01T00:00:00Z");
        setSubmittedAt(later, "2026-08-02T00:00:00Z");

        assertThat(top(problem.id(), 10))
                .extracting(RankingEntry::attemptId)
                .containsExactly(earlier, later);
    }

    @Test
    void 제출_시각을_모르는_행은_뒤로_보낸다() {
        Problem problem = newProblem();
        Long known = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        Long unknown = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        setSubmittedAt(known, "2026-08-01T00:00:00Z");
        dsl.execute("UPDATE attempt SET submitted_at = NULL WHERE id = ?", unknown);

        assertThat(top(problem.id(), 10))
                .extracting(RankingEntry::attemptId)
                .containsExactly(known, unknown);
    }

    @Test
    void 게스트의_제출은_랭킹에_들지_않는다() {
        Problem problem = newProblem();
        submittedAttempt(problem, AttemptOwner.guest(newGuestSession()), 1);

        RankedPage page = rankingQueryRepository.findTop(problem.id(), 10);

        assertThat(page.entries()).isEmpty();
        assertThat(page.totalCount()).isZero();
    }

    @Test
    void 게스트가_더_싸도_등수와_전체_수는_로그인_사용자만_센다() {
        Problem problem = newProblem();
        submittedAttempt(problem, AttemptOwner.guest(newGuestSession()), 1);
        Long mine = submittedAttempt(problem, AttemptOwner.user(ownerId), 2);

        RankedPage page = rankingQueryRepository.findTop(problem.id(), 10);

        assertThat(page.entries()).extracting(RankingEntry::attemptId).containsExactly(mine);
        assertThat(page.entries()).extracting(RankingEntry::rank).containsExactly(1);
        assertThat(page.totalCount()).isEqualTo(1);
    }

    /** 랭킹에서 빠진 게스트에게 남은 길은 로그인 하나뿐이라, 그 길이 열려 있는지를 여기서 지킨다. */
    @Test
    void 게스트가_로그인하면_그때까지_푼_기록이_랭킹에_오른다() {
        Problem problem = newProblem();
        UUID guestSessionId = newGuestSession();
        Long attemptId = submittedAttempt(problem, AttemptOwner.guest(guestSessionId), 1);
        assertThat(top(problem.id(), 10)).isEmpty();

        guestAttemptOwnershipRepository.transferToUser(guestSessionId, ownerId);

        RankingEntry entry = top(problem.id(), 10).getFirst();
        assertThat(entry.attemptId()).isEqualTo(attemptId);
        assertThat(entry.userId()).isEqualTo(ownerId);
        assertThat(entry.nickname()).isEqualTo("test owner");
    }

    @Test
    void limit을_넘는_행은_돌려주지_않지만_전체_수는_그대로다() {
        Problem problem = newProblem();
        submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        submittedAttempt(problem, AttemptOwner.user(ownerId), 2);
        submittedAttempt(problem, AttemptOwner.user(ownerId), 3);

        RankedPage page = rankingQueryRepository.findTop(problem.id(), 2);

        // 잘라낸 쪽과 전체 수를 한 번에 돌려주는 것이 이 조회의 계약이다.
        assertThat(page.entries()).hasSize(2);
        assertThat(page.totalCount()).isEqualTo(3);
    }

    @Test
    void 다른_문제의_제출은_섞이지_않는다() {
        Problem problem = newProblem();
        Problem other = newProblem();
        Long mine = submittedAttempt(problem, AttemptOwner.user(ownerId), 1);
        submittedAttempt(other, AttemptOwner.user(ownerId), 1);

        assertThat(top(problem.id(), 10))
                .extracting(RankingEntry::attemptId)
                .containsExactly(mine);
    }

    @Test
    void 내_어템프트_중_가장_좋은_한_줄을_찾는다() {
        Problem problem = newProblem();
        AttemptOwner owner = AttemptOwner.user(ownerId);
        Long expensive = submittedAttempt(problem, owner, 3);
        Long cheap = submittedAttempt(problem, owner, 1);
        submittedAttempt(problem, AttemptOwner.user(otherUserId()), 2);

        RankingEntry best = rankingQueryRepository.findBestOf(problem.id(), ownerId).orElseThrow();

        assertThat(best.attemptId()).isEqualTo(cheap);
        assertThat(best.rank()).isEqualTo(1);
        assertThat(best.attemptId()).isNotEqualTo(expensive);
    }

    @Test
    void 자격을_갖춘_내_어템프트가_없으면_비어_있다() {
        Problem problem = newProblem();
        submittedAttempt(problem, AttemptOwner.user(otherUserId()), 1);

        assertThat(rankingQueryRepository.findBestOf(problem.id(), ownerId)).isEmpty();
    }

    /** 턴을 turns개 쌓고 제출한 뒤, 마지막 턴에 대한 SUCCEEDED 실행을 남긴다. */
    /** 전체 수까지 볼 필요가 없는 검사가 대부분이라 상위 줄만 꺼낸다. */
    private List<RankingEntry> top(Long problemId, int limit) {
        return rankingQueryRepository.findTop(problemId, limit).entries();
    }

    private Long submittedAttempt(Problem problem, AttemptOwner owner, int turns) {
        AttemptView attempt = attemptService.startAttempt(problem.id(), owner, null);

        for (int index = 0; index < turns; index++) {
            attemptService.addTurn(attempt.id(), owner, "턴 " + index, null);
        }

        attemptService.submit(attempt.id(), owner);
        succeedRun(attempt.id(), turns - 1);

        return attempt.id();
    }

    private void succeedRun(Long attemptId, int turnOrdinal) {
        insertRun(attemptId, turnOrdinal, "SUCCEEDED");
    }

    private void insertRun(Long attemptId, int turnOrdinal, String status) {
        dsl.execute(
                "INSERT INTO code_run (id, attempt_id, turn_ordinal, status, created_at) VALUES (?, ?, ?, ?, now())",
                UUID.randomUUID(), attemptId, turnOrdinal, status
        );
    }

    private void deleteRuns(Long attemptId) {
        dsl.execute("DELETE FROM code_run WHERE attempt_id = ?", attemptId);
    }

    private void setSubmittedAt(Long attemptId, String submittedAt) {
        dsl.update(table(name("attempt")))
                .set(field(name("attempt", "submitted_at"), SQLDataType.INSTANT), Instant.parse(submittedAt))
                .where(field(name("attempt", "id"), SQLDataType.BIGINT).eq(attemptId))
                .execute();
    }

    private UUID newGuestSession() {
        UUID guestSessionId = UUID.randomUUID();
        dsl.execute(
                "INSERT INTO guest_session (id, token_hash, created_at, expires_at)"
                        + " VALUES (?, ?, now(), now() + interval '1 hour')",
                guestSessionId, guestSessionId.toString()
        );

        return guestSessionId;
    }

    private Long otherUserId() {
        return userRepository.save(User.create(
                "other-" + System.nanoTime(), "{noop}password", "다른 사람", System.nanoTime() + "@example.com")).id();
    }

    private Problem newProblem() {
        return problemRepository.save(new Problem(
                "ranking-" + System.nanoTime(),
                "랭킹 문제",
                "명세",
                List.of(new ProblemFile("src/main/java/Main.java", "class Main {}")),
                List.of()
        ));
    }
}
