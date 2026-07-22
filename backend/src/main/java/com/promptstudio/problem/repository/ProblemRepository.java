package com.promptstudio.problem.repository;

import com.promptstudio.problem.entity.Problem;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository {

    List<Problem> findAll();

    Optional<Problem> findById(Long id);
}
