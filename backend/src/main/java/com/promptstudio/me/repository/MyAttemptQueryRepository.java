package com.promptstudio.me.repository;

import com.promptstudio.me.domain.SubmittedAttempt;

import java.util.List;

public interface MyAttemptQueryRepository {

    List<SubmittedAttempt> findSubmittedByUserId(Long userId);
}
