package com.promptstudio.problem.repository;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface ProblemJpaRepository extends JpaRepository<Problem, Long>, ProblemRepository {

    @Override
    @EntityGraph(attributePaths = "files")
    @Query("select p from Problem p order by p.id")
    List<Problem> findAll();

    @Override
    @EntityGraph(attributePaths = "files")
    Optional<Problem> findById(Long id);

    /**
     * testFiles를 위 두 조회의 EntityGraph에 넣지 않고 따로 읽는다. 두 컬렉션을 한 쿼리로 조인하면
     * 카테시안 곱이 되고, 노출 경로가 쓰는 조회에 테스트가 딸려 오지도 않는다.
     */
    @Override
    @Query("select f from Problem p join p.testFiles f where p.id = :problemId order by index(f)")
    List<ProblemFile> findTestFiles(@Param("problemId") Long problemId);

    @Override
    @Query("select p.language from Problem p where p.id = :problemId")
    Optional<String> findLanguageById(@Param("problemId") Long problemId);
}
