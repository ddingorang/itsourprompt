package com.promptstudio.attempt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 턴에서 AI가 호출한 툴 한 건의 최소 기록.
 *
 * <p>path는 대상 파일이 없는 툴(list_files)에서 null이다.
 */
@Embeddable
public record ToolCallEntry(

        @Column(nullable = false, length = 50)
        String tool,

        @Column(length = 500)
        String path
) {

    /**
     * 툴 이름의 정본. 어댑터의 툴 등록, 시스템 프롬프트, API 문서 예시가 모두 이 상수를 참조한다.
     */
    public static final String LIST_FILES = "list_files";
    public static final String READ_FILE = "read_file";
    public static final String EDIT_FILE = "edit_file";
}
