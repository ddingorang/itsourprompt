package com.promptstudio.problem.repository;

import com.promptstudio.problem.domain.Problem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
