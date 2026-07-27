package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.problem.domain.Problem;

public interface FeedbackGenerator {

    String generate(Problem problem, Attempt attempt);
}
