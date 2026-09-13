package com.github.stormino.savonarola.moderation;

public enum OperatingMode {
    /** Dry run: judge and log everything, never act — not even on admin command. */
    LOG_ONLY,
    /** Judge, propose an action, wait for an admin to run /execute. */
    ON_DEMAND_ACTION,
    /** Judge and act autonomously above the confidence threshold. */
    LIVE_ACTION
}
