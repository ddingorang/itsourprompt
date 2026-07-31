package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.AttemptLlmCall;

import java.util.List;

public interface LlmCallRepository {

    /**
     * 호출자의 트랜잭션에 참여한다 — 턴과 같은 커밋으로 묶기 위해 자체 트랜잭션을 열지 않는다.
     *
     * <p>타입 파라미터는 CrudRepository의 saveAll을 그대로 구현하기 위한 것이다 — 시그니처가 다르면
     * 같은 이름의 두 메서드가 지워진 뒤 충돌한다.
     */
    <S extends AttemptLlmCall> List<S> saveAll(Iterable<S> calls);

    List<AttemptLlmCall> findByAttemptId(Long attemptId);
}
