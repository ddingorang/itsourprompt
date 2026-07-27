package com.promptstudio.attempt.repository;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.problem.domain.ProblemFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAttemptRepositoryTest {

    private final MemoryAttemptRepository attemptRepository = new MemoryAttemptRepository();

    @Test
    void 저장하면_ID를_순차적으로_부여한다() {
        Attempt first = attemptRepository.save(newAttempt());
        Attempt second = attemptRepository.save(newAttempt());

        assertThat(first.id()).isEqualTo(1L);
        assertThat(second.id()).isEqualTo(2L);
    }

    @Test
    void 저장한_어템프트를_ID로_조회한다() {
        Attempt saved = attemptRepository.save(newAttempt());

        Optional<Attempt> found = attemptRepository.findById(saved.id());

        assertThat(found).contains(saved);
    }

    @Test
    void 같은_ID로_저장하면_기존_어템프트를_교체한다() {
        Attempt saved = attemptRepository.save(newAttempt());
        Attempt updated = new Attempt(
                saved.id(),
                saved.problemId(),
                List.of(new ProblemFile("src/Main.java", "수정된 내용")),
                saved.turns()
        );

        attemptRepository.save(updated);

        assertThat(attemptRepository.findById(saved.id())).contains(updated);
    }

    @Test
    void 없는_ID면_빈_Optional을_반환한다() {
        assertThat(attemptRepository.findById(999L)).isEmpty();
    }

    private Attempt newAttempt() {
        return new Attempt(null, 1L, List.of(new ProblemFile("src/Main.java", "class Main {}")), List.of());
    }
}
