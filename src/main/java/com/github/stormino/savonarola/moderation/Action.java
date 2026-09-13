package com.github.stormino.savonarola.moderation;

public record Action(ActionType type, int durationMinutes, int rung) {

    public static Action mute(int durationMinutes, int rung) {
        return new Action(ActionType.MUTE, durationMinutes, rung);
    }

    public static Action adminReview(int rung) {
        return new Action(ActionType.ADMIN_REVIEW, 0, rung);
    }
}
