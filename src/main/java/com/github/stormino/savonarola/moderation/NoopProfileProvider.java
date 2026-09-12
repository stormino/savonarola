package com.github.stormino.savonarola.moderation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Placeholder until the nightly profile job exists. */
@Component
@ConditionalOnMissingBean(ProfileProvider.class)
public class NoopProfileProvider implements ProfileProvider {

    @Override
    public String profileFor(long userId) {
        return null;
    }

    @Override
    public int negativeInteractionCount(long senderId, long targetId) {
        return 0;
    }
}
