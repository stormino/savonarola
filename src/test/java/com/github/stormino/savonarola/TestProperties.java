package com.github.stormino.savonarola;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.moderation.OperatingMode;

import java.util.List;
import java.util.Map;

/**
 * Fixture for the configuration record. Every positional construction lives here, so
 * adding a field to SavonarolaProperties breaks one file rather than every test using it.
 */
public final class TestProperties {

    public static final long MAIN_CHAT = -1001234567890L;
    public static final long ADMIN_CHAT = -1009876543210L;
    public static final long OWNER_CHAT = 226449030L;

    private TestProperties() {}

    public static SavonarolaProperties with(OperatingMode mode) {
        return with(mode, 0.6);
    }

    public static SavonarolaProperties with(OperatingMode mode, double confidenceThreshold) {
        return build(mode, confidenceThreshold, defaultRuleSet(), defaultProfile(), defaultLlm(),
                OWNER_CHAT);
    }

    /** No owner chat configured, so the verbose stream has nowhere to go. */
    public static SavonarolaProperties withoutOwnerChat(OperatingMode mode) {
        return build(mode, 0.6, defaultRuleSet(), defaultProfile(), defaultLlm(), 0L);
    }

    public static SavonarolaProperties withRuleSet(int maxExamplesPerRule) {
        return build(OperatingMode.LOG_ONLY, 0.6,
                new SavonarolaProperties.RuleSet(maxExamplesPerRule), defaultProfile(), defaultLlm(), OWNER_CHAT);
    }

    public static SavonarolaProperties withProfile(SavonarolaProperties.Profile profile) {
        return build(OperatingMode.LOG_ONLY, 0.6, defaultRuleSet(), profile, defaultLlm(),
                OWNER_CHAT);
    }

    public static SavonarolaProperties withLlm(SavonarolaProperties.Llm llm) {
        return build(OperatingMode.LOG_ONLY, 0.6, defaultRuleSet(), defaultProfile(), llm,
                OWNER_CHAT);
    }

    public static SavonarolaProperties.RuleSet defaultRuleSet() {
        return new SavonarolaProperties.RuleSet(6);
    }

    public static SavonarolaProperties.Profile defaultProfile() {
        return new SavonarolaProperties.Profile(true, "0 0 4 * * *", 30, 20, 8, 25, 15, 60);
    }

    public static SavonarolaProperties.Llm defaultLlm() {
        return new SavonarolaProperties.Llm("test", Map.of("test",
                new SavonarolaProperties.Llm.Provider("http://localhost", "key", Map.of(),
                        List.of("primary", "fallback"), "profile")));
    }

    private static SavonarolaProperties build(OperatingMode mode, double confidenceThreshold,
                                              SavonarolaProperties.RuleSet ruleSet,
                                              SavonarolaProperties.Profile profile,
                                              SavonarolaProperties.Llm llm,
                                              long ownerChat) {
        return new SavonarolaProperties(
                mode,
                new SavonarolaProperties.Telegram(MAIN_CHAT, ADMIN_CHAT, ownerChat, "token"),
                new SavonarolaProperties.Decision(confidenceThreshold),
                new SavonarolaProperties.PatternDetection(3, 30),
                new SavonarolaProperties.Escalation(List.of(5, 30, 120, 1440), 30),
                new SavonarolaProperties.MessageStore(90, 15),
                new SavonarolaProperties.Conflict(15, 3, 300, 600, 5, 40, 15, 30),
                ruleSet,
                new SavonarolaProperties.Group(14, 40),
                llm,
                profile,
                new SavonarolaProperties.Health(3, "SYSTEM"),
                new SavonarolaProperties.ActionAnnouncement(
                        true, "Utente {user} mutato per {duration}.", true));
    }
}
