package com.promptstudio.gitlab;

import com.promptstudio.problem.port.ProblemSourceClient;
import com.promptstudio.problem.port.ProblemSourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GitLab REST API로 문제 저장소를 읽는다. 전송 계층 예외는 포트 밖으로 새지 않게 번역한다.
 */
@Component
public class GitLabProblemSourceClient implements ProblemSourceClient {

    private static final String COMMITS_PATH = "/api/v4/projects/{projectId}/repository/commits";
    private static final String ARCHIVE_PATH = "/api/v4/projects/{projectId}/repository/archive.zip";
    private static final Logger log = LoggerFactory.getLogger(GitLabProblemSourceClient.class);

    private final RestClient restClient;
    private final String projectId;
    private final String branch;

    public GitLabProblemSourceClient(
            @Value("${PROBLEM_REPO_TOKEN}") String token,
            @Value("${PROBLEM_REPO_BASE_URL:https://lab.ssafy.com}") String baseUrl,
            @Value("${PROBLEM_REPO_PROJECT_ID:1419080}") String projectId,
            @Value("${PROBLEM_REPO_BRANCH:master}") String branch
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("PRIVATE-TOKEN", token)
                .build();
        this.projectId = projectId;
        this.branch = branch;
    }

    @Override
    public Optional<String> latestCommitSha() {
        List<Map<String, Object>> commits = call("최신 커밋 조회", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(COMMITS_PATH)
                        .queryParam("ref_name", branch)
                        .queryParam("per_page", 1)
                        .build(projectId))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }));

        if (commits == null || commits.isEmpty()) {
            return Optional.empty();
        }

        Object sha = commits.getFirst().get("id");

        if (sha == null) {
            throw new ProblemSourceException("문제 저장소 커밋 응답에 id가 없습니다.");
        }

        return Optional.of(String.valueOf(sha));
    }

    @Override
    public byte[] downloadArchiveZip(String sha) {
        byte[] archive = call("아카이브 다운로드", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(ARCHIVE_PATH)
                        .queryParam("sha", sha)
                        .build(projectId))
                .retrieve()
                .body(byte[].class));

        if (archive == null) {
            throw new ProblemSourceException("문제 저장소 아카이브 응답이 비어 있습니다.");
        }

        return archive;
    }

    private <T> T call(String operation, RestCall<T> restCall) {
        try {
            return restCall.execute();
        } catch (RestClientResponseException exception) {
            log.error(
                    "GitLab API 호출 실패: operation={}, projectId={}, status={}, responseBody={}",
                    operation,
                    projectId,
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString(),
                    exception
            );

            throw new ProblemSourceException("문제 저장소 " + operation + "에 실패했습니다.", exception);
        } catch (RestClientException exception) {
            log.error("GitLab API 호출 실패: operation={}, projectId={}", operation, projectId, exception);

            throw new ProblemSourceException("문제 저장소 " + operation + "에 실패했습니다.", exception);
        }
    }

    @FunctionalInterface
    private interface RestCall<T> {

        T execute();
    }
}
