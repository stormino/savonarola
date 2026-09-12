package com.github.stormino.savonarola.llm;

import com.github.stormino.savonarola.moderation.Judgment;

public interface LlmJudge {
    Judgment judge(JudgmentInput input);
}
