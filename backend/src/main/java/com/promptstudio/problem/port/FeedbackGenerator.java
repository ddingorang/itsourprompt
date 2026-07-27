package com.promptstudio.problem.port;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.domain.Submission;

public interface FeedbackGenerator {

    String generate(Problem problem, Submission submission);
}
