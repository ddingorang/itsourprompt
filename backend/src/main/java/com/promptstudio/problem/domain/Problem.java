package com.promptstudio.problem.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "problem")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "spec_md", nullable = false, columnDefinition = "text")
    private String specMd;

    @ElementCollection
    @CollectionTable(name = "problem_file", joinColumns = @JoinColumn(name = "problem_id"))
    @OrderColumn(name = "ordinal")
    private List<ProblemFile> files = new ArrayList<>();

    protected Problem() {
    }

    public Problem(String title, String specMd, List<ProblemFile> files) {
        this(null, title, specMd, files);
    }

    public Problem(Long id, String title, String specMd, List<ProblemFile> files) {
        this.id = id;
        this.title = title;
        this.specMd = specMd;
        this.files = new ArrayList<>(files);
    }

    public Long id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String specMd() {
        return specMd;
    }

    public List<ProblemFile> files() {
        return List.copyOf(files);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Problem problem)) {
            return false;
        }

        return id != null && id.equals(problem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
