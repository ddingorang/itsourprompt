package com.promptstudio.relay.repository;

import com.promptstudio.relay.domain.RelayRoom;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RelayRoomRepository {

    RelayRoom save(RelayRoom room);

    Optional<RelayRoom> findById(Long id);

    /**
     * 입장을 받는 중(WAITING)인 방들, 최신 순. 로비 목록이 쓴다.
     * 상한 50개 — 페이지네이션 전까지의 안전판이고, idx_relay_room_status가 이 정렬 그대로다.
     */
    List<RelayRoom> findWaitingRooms();

    /**
     * 워커 결과 이벤트가 방을 되찾는 경로. 베이스라인이든 턴 채점이든 진행 중인 run은
     * current_run_id에 있고, 릴레이 run이 아니면 빈 값이 나와 이벤트를 무시하게 된다.
     */
    Optional<RelayRoom> findByCurrentRunId(java.util.UUID runId);

    /**
     * 채점 마감을 넘긴 방들. 스케줄러가 채점 없이 좌석을 전진시키는 데 쓴다.
     */
    List<RelayRoom> findExpiredGrading(Instant now);

    /**
     * 입력 마감을 넘긴 방들(주자가 프롬프트를 내지 않음). 스케줄러가 그 턴을 건너뛰는 데 쓴다.
     */
    List<RelayRoom> findExpiredTurnInput(Instant now);

    /**
     * "지금 이 턴 번호가 맞고 주자를 기다리는 중이면 생성 중으로 바꾼다"를 원자적으로 수행한다.
     *
     * <p>턴 순서 검사 후 LLM 생성이 수십 초 걸리는 동안 같은 사람이 두 번 누르거나 다음 사람이
     * 새치기할 수 있다. 검사와 전이를 한 문장으로 묶어 DB가 레이스를 막는다 —
     * uq_code_run_active 부분 유니크 인덱스와 같은 철학이다.
     *
     * @return 전이에 성공했으면 true. false면 이미 다른 요청이 턴을 잡았거나 상태가 바뀐 뒤다
     */
    boolean tryClaimTurn(Long roomId, int expectedTurnIndex);

    /**
     * 방 행에 쓰기 락을 걸고 읽는다. 정원 초과를 막는 데 쓴다.
     *
     * <p>정원 검사는 "참가자 수를 세고, 미달이면 넣는다"인데 READ COMMITTED에서는 동시 입장 두 건이
     * 모두 미달을 보고 통과한다. 조건부 INSERT로는 막을 수 없어(다른 행을 세는 조건이다) 방 행을
     * 직렬화 지점으로 쓴다. 입장은 초당 수백 건이 오는 경로가 아니므로 락 비용이 문제되지 않는다.
     *
     * <p>같은 사람의 중복 입장은 이 락이 아니라 {@code uq_relay_participant_user}가 막는다.
     */
    Optional<RelayRoom> findByIdForUpdate(Long id);
}
