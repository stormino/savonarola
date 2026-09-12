package com.github.stormino.savonarola;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.moderation.OperatingMode;

import java.util.List;
import java.util.Map;

/** Fixture for the configuration record, so tests only state what they care about. */
public final class TestProperties {

    public static final long MAIN_CHAT = -1001234567890L;
    public static final long ADMIN_CHAT = -1009876543210L;

    private TestProperties() {}

    public static SavonarolaProperties with(OperatingMode mode) {
        return with(mode, 0.6);
    }

    public static SavonarolaProperties with(OperatingMode mode, double confidenceThreshold) {
        return new SavonarolaProperties(
                mode,
                new SavonarolaProperties.Telegram(MAIN_CHAT, ADMIN_CHAT, "token"),
                new SavonarolaProperties.Decision(confidenceThreshold),
                new SavonarolaProperties.PatternDetection(3, 30),
                new SavonarolaProperties.Escalation(List.of(5, 30, 120, 1440), 30),
                new SavonarolaProperties.MessageStore(90, 15),
                new SavonarolaProperties.Llm("test", Map.of("test",
                        new SavonarolaProperties.Llm.Provider("http://localhost", "key", Map.of(),
                                List.of("primary", "fallback"), "profile"))),
                new SavonarolaProperties.Profile(true, "0 0 4 * * *", 30, 20, 8, 25, 15, 60),
                new SavonarolaProperties.Health(3, "SYSTEM"),
                new SavonarolaProperties.ActionAnnouncement(
                        true, "Utente {user} mutato per {duration}.", true));
    }
}
