package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.AttemptFeedback;
import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.TurnTestResults;
import com.promptstudio.problem.domain.ProblemView;

public interface FeedbackGenerator {

    /**
     * @param testResults 턴별 채점 결과. 실행 기록이 없으면 {@link TurnTestResults#EMPTY}다
     */
    AttemptFeedback generate(ProblemView problem, AttemptView attempt, TurnTestResults testResults);
}
