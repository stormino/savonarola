package com.github.stormino.savonarola.moderation;

/**
 * Placeholder until the nightly profile job exists. Declared as a @Bean in ProfileConfig,
 * not a @Component: @ConditionalOnMissingBean is only evaluated on @Bean methods.
 */
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
