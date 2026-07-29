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

    /**
     * 문제 저장소의 최상위 디렉토리명. 시더 시절에 만들어진 행은 비어 있을 수 있다.
     */
    @Column(name = "slug")
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(name = "spec_md", nullable = false, columnDefinition = "text")
    private String specMd;

    @ElementCollection
    @CollectionTable(name = "problem_file", joinColumns = @JoinColumn(name = "problem_id"))
    @OrderColumn(name = "ordinal")
    private List<ProblemFile> files = new ArrayList<>();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Problem() {
    }

    public Problem(String slug, String title, String specMd, List<ProblemFile> files) {
        this(null, slug, title, specMd, files);
    }

    public Problem(Long id, String slug, String title, String specMd, List<ProblemFile> files) {
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.specMd = specMd;
        this.files = new ArrayList<>(files);
    }

    /**
     * 저장소에서 다시 읽어온 내용으로 갈아끼운다. slug는 문제의 식별자라 바뀌지 않는다.
     */
    public void updateFrom(String title, String specMd, List<ProblemFile> files) {
        this.title = title;
        this.specMd = specMd;
        this.files.clear();
        this.files.addAll(files);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long id() {
        return id;
    }

    public String slug() {
        return slug;
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

    public boolean active() {
        return active;
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
