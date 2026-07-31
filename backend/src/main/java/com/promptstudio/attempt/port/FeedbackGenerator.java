package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.problem.domain.ProblemView;

public interface FeedbackGenerator {

    AttemptFeedback generate(ProblemView problem, AttemptView attempt);
}
