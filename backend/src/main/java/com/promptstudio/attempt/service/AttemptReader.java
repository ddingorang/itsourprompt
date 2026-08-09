package com.promptstudio.attempt.service;

import com.promptstudio.attempt.domain.AttemptOwner;
import com.promptstudio.attempt.domain.AttemptStatus;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.repository.AttemptQueryRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 어템프트를 "요청자가 볼 수 있는가"로 갈라 조회 seam에서 읽어 온다. 공개 범위가 정해지는 곳은 여기뿐이다.
 *
 * <p>어템프트·피드백·실행 기록이 각자 같은 조합을 들고 있으면, 공개 범위를 넓히는 변경이 일부 경로에만
 * 반영돼도 컴파일러도 테스트도 잡지 못한다 — 같은 어템프트가 어느 엔드포인트로 물어보느냐에 따라
 * 보이기도 하고 안 보이기도 한다.
 *
 * <p>볼 수 없는 어템프트는 403이 아니라 404다 — 남의 것은 존재조차 알리지 않는다.
 */
@Component
class AttemptReader {

    private final AttemptQueryRepository attemptQueryRepository;

    AttemptReader(AttemptQueryRepository attemptQueryRepository) {
        this.attemptQueryRepository = attemptQueryRepository;
    }

    /**
     * 소유자 관문 — 요청자가 주인일 때만 내준다. 제출 여부는 보지 않는다.
     *
     * <p><b>쓰기 경로(턴 추가·제출·실행 요청)는 반드시 이쪽을 쓴다.</b> {@link #requireReadable}로 바꾸면
     * 쓰기의 소유권 보장이 "제출 상태 검사"라는 무관한 조건에 얹혀, 공개 범위를 넓히는 변경이 조용히
     * 쓰기까지 넓힌다. 랭킹이 남의 어템프트 ID를 공개하므로 그 순간 표에서 긁은 ID로 남의 어템프트에
     * LLM 비용을 태우거나, 남의 빌드/실행 컨테이너를 띄우고 {@code uq_code_run_active}(어템프트당
     * 미완료 실행 1건)로 상대의 실행을 409로 막을 수 있다.
     */
    AttemptView requireOwned(Long attemptId, AttemptOwner owner) {
        return findOwned(attemptId, owner)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    /**
     * 공개 읽기 — 내 것이거나, 제출 완료(SUBMITTED)라 누구에게나 열린 것. <b>읽기 경로 전용이다.</b>
     *
     * <p>소유자 조회를 먼저 타므로 자기 어템프트는 상태와 무관하게 1쿼리다. 남의 SUBMITTED만 2쿼리를 쓴다.
     */
    AttemptView requireReadable(Long attemptId, AttemptOwner requester) {
        return findOwned(attemptId, requester)
                .or(() -> attemptQueryRepository.findById(attemptId)
                        .filter(attempt -> attempt.status() == AttemptStatus.SUBMITTED))
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    private Optional<AttemptView> findOwned(Long attemptId, AttemptOwner owner) {
        if (owner.isUser()) {
            return attemptQueryRepository.findByIdAndUserId(attemptId, owner.userId());
        }

        return attemptQueryRepository.findByIdAndGuestSessionId(attemptId, owner.guestSessionId());
    }
}
