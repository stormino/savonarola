package com.github.stormino.savonarola.llm;

/**
 * One message the judge says broke a rule. The window is judged in a single call and only
 * violations come back, so silence about a message is the verdict that it was fine.
 */
public record Violation(long messageId, String ruleId, double confidence, String reasoning) {}
