package com.github.stormino.savonarola.moderation;

/**
 * Supplies the tone/dynamic priors the judge uses to disambiguate ambiguous messages.
 * Batch-built profiles land here; for now this is a seam with a null-object default.
 */
public interface ProfileProvider {

    String profileFor(long userId);

    /** Count of negative interactions sender -> target inside the detection window. */
    int negativeInteractionCount(long senderId, long targetId);
}
