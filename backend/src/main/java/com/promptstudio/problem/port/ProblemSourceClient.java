package com.promptstudio.problem.port;

import java.util.Optional;

/**
 * 문제 원천 저장소를 읽는 포트. 전송 계층의 실패는 모두 {@link ProblemSourceException}으로 번역된다.
 */
public interface ProblemSourceClient {

    /**
     * @return 대상 브랜치의 최신 커밋 SHA. 커밋이 하나도 없으면 빈 값.
     */
    Optional<String> latestCommitSha();

    byte[] downloadArchiveZip(String sha);
}
