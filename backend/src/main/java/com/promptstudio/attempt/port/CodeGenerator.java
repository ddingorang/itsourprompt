package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.Attempt;
import com.promptstudio.attempt.domain.GeneratedCode;

public interface CodeGenerator {

    GeneratedCode generate(Attempt attempt, String userPrompt);
}
