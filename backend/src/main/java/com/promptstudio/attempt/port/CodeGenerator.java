package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;
import com.promptstudio.problem.domain.ProblemView;

public interface CodeGenerator {

    GeneratedCode generate(ProblemView problem, AttemptView attempt, String userPrompt);
}
