package com.promptstudio.attempt.domain;

import com.promptstudio.attempt.exception.AttemptAlreadySubmittedException;
import com.promptstudio.attempt.exception.FeedbackTurnCountMismatchException;
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

    /**
     * 시작 스켈레톤. 한 번 정해지면 바뀌지 않고, 현재 상태는 여기에 턴을 재생해 얻는다.
     */
    @ElementCollection
    @CollectionTable(name = "attempt_file", joinColumns = @JoinColumn(name = "attempt_id"))
    @OrderColumn(name = "ordinal")
    private List<ProblemFile> baseFiles = new ArrayList<>();

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

    private Attempt(Long problemId, List<ProblemFile> baseFiles) {
        this.problemId = problemId;
        this.baseFiles = new ArrayList<>(baseFiles);
    }

    public static Attempt start(Problem problem) {
        return new Attempt(problem.id(), problem.files());
    }

    public void applyTurn(String userPrompt, GeneratedCode generated) {
        if (status == AttemptStatus.SUBMITTED) {
            throw new AttemptAlreadySubmittedException(id);
        }

        turns.add(new Turn(
                userPrompt,
                generated.summary(),
                FileChanges.diff(currentFiles(), generated.files()),
                generated.toolCalls()
        ));
    }

    public void submit(AttemptFeedback feedback) {
        if (status == AttemptStatus.SUBMITTED) {
            throw new AttemptAlreadySubmittedException(id);
        }

        List<String> turnFeedbacks = feedback.turnFeedbacks();

        if (turnFeedbacks.size() != turns.size()) {
            throw new FeedbackTurnCountMismatchException(turns.size(), turnFeedbacks.size());
        }

        for (int index = 0; index < turns.size(); index++) {
            turns.get(index).applyFeedback(turnFeedbacks.get(index));
        }

        this.status = AttemptStatus.SUBMITTED;
        this.feedback = feedback.overall();
    }

    public Long id() {
        return id;
    }

    public Long problemId() {
        return problemId;
    }

    public List<ProblemFile> baseFiles() {
        return List.copyOf(baseFiles);
    }

    public List<ProblemFile> currentFiles() {
        return FileReplay.head(baseFiles, turns, Turn::changes);
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
