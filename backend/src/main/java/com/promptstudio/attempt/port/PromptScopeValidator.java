package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.PromptScopeDecision;
import com.promptstudio.problem.domain.ProblemView;

public interface PromptScopeValidator {

    PromptScopeDecision validate(ProblemView problem, String userPrompt);
}
