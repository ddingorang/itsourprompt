package com.promptstudio.problem.port;

import com.promptstudio.problem.domain.GeneratedCode;
import com.promptstudio.problem.domain.Problem;

public interface CodeGenerator {

    GeneratedCode generate(Problem problem, String userPrompt);
}
