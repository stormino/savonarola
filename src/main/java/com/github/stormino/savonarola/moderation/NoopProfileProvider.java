package com.github.stormino.savonarola.moderation;

/**
 * Placeholder until the nightly profile job exists. Registered as a fallback bean in
 * {@link com.github.stormino.savonarola.config.ProfileConfig} rather than annotated
 * directly: @ConditionalOnMissingBean is only evaluated on @Bean methods, so on a
 * @Component it would have silently done nothing.
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
