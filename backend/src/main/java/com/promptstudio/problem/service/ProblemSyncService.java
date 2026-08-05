package com.promptstudio.problem.service;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.SyncState;
import com.promptstudio.problem.port.ProblemSourceClient;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.problem.repository.SyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 문제 저장소의 HEAD가 움직였으면 archive를 통째로 받아 문제 전체를 다시 맞춘다.
 *
 * <p>커밋 SHA 갱신까지 한 트랜잭션이라, 도중에 실패하면 SHA가 남지 않아 다음 주기에 자동으로 재시도된다.
 */
@Service
public class ProblemSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProblemSyncService.class);

    private final ProblemSourceClient problemSourceClient;
    private final ProblemRepository problemRepository;
    private final SyncStateRepository syncStateRepository;

    public ProblemSyncService(
            ProblemSourceClient problemSourceClient,
            ProblemRepository problemRepository,
            SyncStateRepository syncStateRepository
    ) {
        this.problemSourceClient = problemSourceClient;
        this.problemRepository = problemRepository;
        this.syncStateRepository = syncStateRepository;
    }

    @Transactional
    public SyncResult sync() {
        Optional<String> latestCommitSha = problemSourceClient.latestCommitSha();

        if (latestCommitSha.isEmpty()) {
            log.info("문제 저장소에 커밋이 없어 동기화를 건너뜁니다.");

            return SyncResult.skip();
        }

        String sha = latestCommitSha.get();
        SyncState syncState = syncStateRepository.findById(SyncState.ID).orElse(null);

        if (syncState != null && sha.equals(syncState.lastCommitSha())) {
            return SyncResult.skip();
        }

        List<ParsedProblem> parsed = ProblemArchiveParser.parse(problemSourceClient.downloadArchiveZip(sha));
        SyncResult result = upsert(parsed);
        recordSync(syncState, sha);

        return result;
    }

    private SyncResult upsert(List<ParsedProblem> parsed) {
        Map<String, Problem> bySlug = new HashMap<>();

        for (Problem problem : problemRepository.findAll()) {
            // slug가 없는 레거시 행은 저장소와 짝지을 수 없으므로 그대로 둔다.
            if (problem.slug() != null) {
                bySlug.put(problem.slug(), problem);
            }
        }

        Set<String> syncedSlugs = new HashSet<>();
        int created = 0;
        int updated = 0;
        int deactivated = 0;

        for (ParsedProblem source : parsed) {
            syncedSlugs.add(source.slug());
            Problem problem = bySlug.get(source.slug());

            if (problem == null) {
                problemRepository.save(new Problem(
                        source.slug(), source.title(), source.specMd(), source.type(), source.language(),
                        source.files(), source.testFiles()));
                created++;
                continue;
            }

            problem.updateFrom(source.title(), source.specMd(), source.type(), source.language(),
                    source.files(), source.testFiles());
            problem.activate();
            problemRepository.save(problem);
            updated++;
        }

        for (Map.Entry<String, Problem> entry : bySlug.entrySet()) {
            Problem problem = entry.getValue();

            if (syncedSlugs.contains(entry.getKey()) || !problem.active()) {
                continue;
            }

            problem.deactivate();
            problemRepository.save(problem);
            deactivated++;
        }

        return new SyncResult(created, updated, deactivated, false);
    }

    private void recordSync(SyncState syncState, String sha) {
        Instant now = Instant.now();

        if (syncState == null) {
            syncStateRepository.save(new SyncState(sha, now));

            return;
        }

        syncState.record(sha, now);
        syncStateRepository.save(syncState);
    }

    public record SyncResult(int created, int updated, int deactivated, boolean skipped) {

        private static SyncResult skip() {
            return new SyncResult(0, 0, 0, true);
        }
    }
}
