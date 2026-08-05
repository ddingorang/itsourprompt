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

    @Column(name = "problem_type", nullable = false, length = 20)
    private String type = "coding";

    /**
     * 채점 언어. 언어별 워커 라우팅과 AI 생성 프롬프트가 이 값을 본다.
     * game 문제는 채점을 하지 않아 이 값을 쓰지 않는다(기본값 java가 그대로 남는다).
     */
    @Column(name = "language", nullable = false, length = 20)
    private String language = "java";

    @ElementCollection
    @CollectionTable(name = "problem_file", joinColumns = @JoinColumn(name = "problem_id"))
    @OrderColumn(name = "ordinal")
    private List<ProblemFile> files = new ArrayList<>();

    /**
     * 채점용 테스트. 사용자와 AI에게 노출하면 안 되므로 스켈레톤과 별도 컬렉션으로 둔다.
     * 노출 경로({@link ProblemView})가 이 필드를 읽지 않는 것이 격리의 전부다.
     */
    @ElementCollection
    @CollectionTable(name = "problem_test_file", joinColumns = @JoinColumn(name = "problem_id"))
    @OrderColumn(name = "ordinal")
    private List<ProblemFile> testFiles = new ArrayList<>();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Problem() {
    }

    public Problem(String slug, String title, String specMd, List<ProblemFile> files, List<ProblemFile> testFiles) {
        this(null, slug, title, specMd, "coding", "java", files, testFiles);
    }

    public Problem(
            Long id,
            String slug,
            String title,
            String specMd,
            List<ProblemFile> files,
            List<ProblemFile> testFiles
    ) {
        this(id, slug, title, specMd, "coding", "java", files, testFiles);
    }

    public Problem(
            String slug,
            String title,
            String specMd,
            String type,
            String language,
            List<ProblemFile> files,
            List<ProblemFile> testFiles
    ) {
        this(null, slug, title, specMd, type, language, files, testFiles);
    }

    public Problem(
            Long id,
            String slug,
            String title,
            String specMd,
            String type,
            String language,
            List<ProblemFile> files,
            List<ProblemFile> testFiles
    ) {
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.specMd = specMd;
        this.type = type;
        this.language = language;
        this.files = new ArrayList<>(files);
        this.testFiles = new ArrayList<>(testFiles);
    }

    /**
     * 저장소에서 다시 읽어온 내용으로 갈아끼운다. slug는 문제의 식별자라 바뀌지 않는다.
     */
    public void updateFrom(String title, String specMd, List<ProblemFile> files, List<ProblemFile> testFiles) {
        updateFrom(title, specMd, "coding", files, testFiles);
    }

    public void updateFrom(
            String title,
            String specMd,
            String type,
            List<ProblemFile> files,
            List<ProblemFile> testFiles
    ) {
        updateFrom(title, specMd, type, "java", files, testFiles);
    }

    public void updateFrom(
            String title,
            String specMd,
            String type,
            String language,
            List<ProblemFile> files,
            List<ProblemFile> testFiles
    ) {
        this.title = title;
        this.specMd = specMd;
        this.type = type;
        this.language = language;
        this.files.clear();
        this.files.addAll(files);
        this.testFiles.clear();
        this.testFiles.addAll(testFiles);
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

    public String type() {
        return type;
    }

    public String language() {
        return language;
    }

    public List<ProblemFile> files() {
        return List.copyOf(files);
    }

    public List<ProblemFile> testFiles() {
        return List.copyOf(testFiles);
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
