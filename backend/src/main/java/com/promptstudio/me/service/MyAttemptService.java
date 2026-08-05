package com.promptstudio.me.service;

import com.promptstudio.me.domain.SubmittedAttempt;
import com.promptstudio.me.repository.MyAttemptQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MyAttemptService {

    private final MyAttemptQueryRepository myAttemptQueryRepository;

    public MyAttemptService(MyAttemptQueryRepository myAttemptQueryRepository) {
        this.myAttemptQueryRepository = myAttemptQueryRepository;
    }

    @Transactional(readOnly = true)
    public List<SubmittedAttempt> getSubmittedAttempts(Long userId) {
        return myAttemptQueryRepository.findSubmittedByUserId(userId);
    }
}
