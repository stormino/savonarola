package com.github.stormino.savonarola.llm;

import java.util.List;

public interface LlmJudge {
    /** Empty when nothing in the window broke a rule, which is the ordinary case. */
    List<Violation> judge(JudgmentInput input);
}
