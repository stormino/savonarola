package com.github.stormino.savonarola.moderation;

public enum DecisionStatus {
    /** Recorded in LOG_ONLY: judged and reported, never actionable. */
    LOGGED,
    PENDING,
    EXECUTED,
    DISMISSED
}
