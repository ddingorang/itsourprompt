package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.repository.AttemptQueryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공개 범위가 여기서만 정해지므로, 읽기와 쓰기의 경계도 여기서 못 박는다 —
 * 서비스마다 API 테스트로 확인하면 새 경로가 생길 때 조용히 빠진다.
 */
class AttemptReaderTest {

    private static final Long ATTEMPT_ID = 7L;
    private static final AttemptOwner OWNER = AttemptOwner.user(1L);
    private static final AttemptOwner OTHER = AttemptOwner.user(2L);

    private final InMemoryAttempts repository = new InMemoryAttempts();
    private final AttemptReader reader = new AttemptReader(repository);

    @Test
    void 소유자는_진행_중인_어템프트를_읽는다() {
        repository.save(attempt(OWNER, AttemptStatus.IN_PROGRESS));

        assertThat(reader.requireReadable(ATTEMPT_ID, OWNER).id()).isEqualTo(ATTEMPT_ID);
        assertThat(reader.requireOwned(ATTEMPT_ID, OWNER).id()).isEqualTo(ATTEMPT_ID);
    }

    @Test
    void 남의_진행_중인_어템프트는_읽을_수_없다() {
        repository.save(attempt(OWNER, AttemptStatus.IN_PROGRESS));

        assertThatThrownBy(() -> reader.requireReadable(ATTEMPT_ID, OTHER))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    @Test
    void 남의_제출된_어템프트는_읽을_수_있다() {
        repository.save(attempt(OWNER, AttemptStatus.SUBMITTED));

        assertThat(reader.requireReadable(ATTEMPT_ID, OTHER).id()).isEqualTo(ATTEMPT_ID);
    }

    /**
     * 이 리팩터링이 지키려는 경계다. 공개 범위가 넓어져도 쓰기 관문은 소유자만 통과시켜야 한다 —
     * 남의 attemptId로 LLM 비용을 태우거나 실행 컨테이너를 띄우는 길이 열리면 안 된다.
     */
    @Test
    void 쓰기_관문은_남의_제출된_어템프트를_열지_않는다() {
        repository.save(attempt(OWNER, AttemptStatus.SUBMITTED));

        assertThatThrownBy(() -> reader.requireOwned(ATTEMPT_ID, OTHER))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    @Test
    void 게스트는_자기_세션의_어템프트만_읽는다() {
        AttemptOwner guest = AttemptOwner.guest(UUID.randomUUID());
        AttemptOwner otherGuest = AttemptOwner.guest(UUID.randomUUID());
        repository.save(attempt(guest, AttemptStatus.IN_PROGRESS));

        assertThat(reader.requireOwned(ATTEMPT_ID, guest).id()).isEqualTo(ATTEMPT_ID);
        assertThatThrownBy(() -> reader.requireOwned(ATTEMPT_ID, otherGuest))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    @Test
    void 없는_어템프트는_둘_다_404다() {
        assertThatThrownBy(() -> reader.requireOwned(ATTEMPT_ID, OWNER))
                .isInstanceOf(AttemptNotFoundException.class);
        assertThatThrownBy(() -> reader.requireReadable(ATTEMPT_ID, OWNER))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    /**
     * 소유자면 1쿼리, 남의 SUBMITTED면 2쿼리 — 소유자 조회를 먼저 타는 순서가 그대로여야 한다.
     */
    @Test
    void 소유자_조회로_끝나면_전체_조회를_하지_않는다() {
        repository.save(attempt(OWNER, AttemptStatus.SUBMITTED));

        reader.requireReadable(ATTEMPT_ID, OWNER);
        assertThat(repository.findByIdCount).isZero();

        reader.requireReadable(ATTEMPT_ID, OTHER);
        assertThat(repository.findByIdCount).isEqualTo(1);
    }

    private AttemptView attempt(AttemptOwner owner, AttemptStatus status) {
        return AttemptView.reconstruct(
                ATTEMPT_ID, 1L, owner, null, List.of(), List.of(), status, null, null, null);
    }

    /**
     * 조회 횟수까지 세는 인메모리 저장소 — 소유자 조회로 끝나면 전체 조회를 하지 않는 것이 공개 읽기의
     * 쿼리 특성이라 그것도 함께 본다.
     *
     * <p>이름이 Repository로 끝나지 않는 것은 {@code PackagingConventionTest}가 그 이름을 영속성 타입으로
     * 보고 {@code <모듈>.repository} 패키지에 있으라고 요구하기 때문이다. 이건 테스트 페이크다.
     */
    private static final class InMemoryAttempts implements AttemptQueryRepository {

        private AttemptView stored;
        private int findByIdCount;

        void save(AttemptView attempt) {
            this.stored = attempt;
        }

        @Override
        public Optional<AttemptView> findById(Long id) {
            findByIdCount++;

            return byId(id);
        }

        @Override
        public Optional<AttemptView> findByIdAndUserId(Long id, Long userId) {
            return byId(id).filter(attempt -> userId.equals(attempt.owner().userId()));
        }

        @Override
        public Optional<AttemptView> findByIdAndGuestSessionId(Long id, UUID guestSessionId) {
            return byId(id).filter(attempt -> guestSessionId.equals(attempt.owner().guestSessionId()));
        }

        private Optional<AttemptView> byId(Long id) {
            return Optional.ofNullable(stored).filter(attempt -> attempt.id().equals(id));
        }
    }
}
