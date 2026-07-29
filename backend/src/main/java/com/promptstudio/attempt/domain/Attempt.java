package com.promptstudio.attempt.domain;

import com.promptstudio.attempt.exception.AttemptAlreadySubmittedException;
import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.ProblemFile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "attempt")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @ElementCollection
    @CollectionTable(name = "attempt_file", joinColumns = @JoinColumn(name = "attempt_id"))
    @OrderColumn(name = "ordinal")
    private List<ProblemFile> currentFiles = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "attempt_id", nullable = false)
    @OrderColumn(name = "ordinal")
    private List<Turn> turns = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "feedback")
    private String feedback;

    protected Attempt() {
    }

    private Attempt(Long problemId, List<ProblemFile> currentFiles) {
        this.problemId = problemId;
        this.currentFiles = new ArrayList<>(currentFiles);
    }

    public static Attempt start(Problem problem) {
        return new Attempt(problem.id(), problem.files());
    }

    public void applyTurn(String userPrompt, GeneratedCode generated) {
        if (status == AttemptStatus.SUBMITTED) {
            throw new AttemptAlreadySubmittedException(id);
        }

        turns.add(new Turn(userPrompt, generated.summary(), FileChanges.diff(currentFiles, generated.files())));
        currentFiles.clear();
        currentFiles.addAll(generated.files());
    }

    public void submit(String feedback) {
        if (status == AttemptStatus.SUBMITTED) {
            throw new AttemptAlreadySubmittedException(id);
        }

        this.status = AttemptStatus.SUBMITTED;
        this.feedback = feedback;
    }

    public Long id() {
        return id;
    }

    public Long problemId() {
        return problemId;
    }

    public List<ProblemFile> currentFiles() {
        return List.copyOf(currentFiles);
    }

    public List<Turn> turns() {
        return List.copyOf(turns);
    }

    public AttemptStatus status() {
        return status;
    }

    public String feedback() {
        return feedback;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Attempt attempt)) {
            return false;
        }

        return id != null && id.equals(attempt.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
