package com.promptstudio.problem.repository;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository {

    Problem save(Problem problem);

    List<Problem> findAll();

    Optional<Problem> findById(Long id);

    /**
     * 여러 문제를 id로 한 번에 읽는다. 릴레이 로비가 방 목록에 문제 제목을 붙이는 데 쓴다 —
     * EntityGraph 없는 기본 조회라 files는 딸려 오지 않는다.
     */
    List<Problem> findAllById(Iterable<Long> ids);

    /**
     * 존재만 확인한다. 문제 명세(spec_md)는 수 KB라, 있는지만 알면 되는 곳에서 엔티티를 통째로 꺼내지 않는다.
     */
    boolean existsById(Long id);

    /**
     * 채점용 테스트만 따로 읽는다. 엔티티를 거치지 않는 것이 핵심이다 — 테스트를 실어야 하는 곳은
     * 워커로 보내는 경로뿐이고, {@link Problem}을 통째로 꺼내 두면 노출 경로에 딸려 갈 수 있다.
     *
     * @return 저장 순서대로. 테스트가 없는 문제면 빈 목록
     */
    List<ProblemFile> findTestFiles(Long problemId);

    /**
     * 채점 언어만 읽는다. 채점 요청마다 부르는 경로라 수 KB짜리 명세가 딸린 엔티티를 꺼내지 않는다.
     *
     * @return 없는 문제면 Optional.empty()
     */
    Optional<String> findLanguageById(Long problemId);
}
