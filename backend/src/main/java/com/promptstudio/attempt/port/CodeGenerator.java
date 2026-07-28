package com.promptstudio.attempt.port;

import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.attempt.domain.GeneratedCode;

public interface CodeGenerator {

    GeneratedCode generate(AttemptView attempt, String userPrompt);
}
