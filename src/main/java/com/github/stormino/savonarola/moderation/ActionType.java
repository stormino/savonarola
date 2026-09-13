package com.github.stormino.savonarola.moderation;

public enum ActionType {
    MUTE,
    /** Terminal rung of the ladder: the bot stops acting and hands over to a human. */
    ADMIN_REVIEW
}
