package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.problem.domain.ProblemView;

public interface FeedbackGenerator {

    String generate(ProblemView problem, AttemptView attempt);
}
