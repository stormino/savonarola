package com.github.stormino.savonarola.moderation;

/** Raw verdict returned by the LLM, before any policy is applied. */
public record Judgment(boolean violated, String ruleId, double confidence, String reasoning) {

    public static Judgment noViolation(String reasoning) {
        return new Judgment(false, null, 0.0, reasoning);
    }
}
