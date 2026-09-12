package com.github.stormino.savonarola.config;

import com.github.stormino.savonarola.moderation.OperatingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "savonarola")
public record SavonarolaProperties(
        OperatingMode operatingMode,
        Telegram telegram,
        Decision decision,
        PatternDetection patternDetection,
        Escalation escalation,
        MessageStore messageStore,
        Llm llm,
        Profile profile,
        Health health,
        ActionAnnouncement actionAnnouncement
) {
    public record Telegram(long mainChatId, long adminChatId, String token) {}

    public record Decision(double confidenceThreshold) {}

    public record PatternDetection(int negativeInteractionThreshold, int windowDays) {}

    /** Ladder entries are minutes; the terminal rung is ADMIN_REVIEW (no auto action). */
    public record Escalation(List<Integer> ladderMinutes, int decayAfterDays) {}

    public record MessageStore(int retentionDays, int contextWindowSize) {}

    /** Caps exist because every user and every pair costs one call against a shared budget. */
    public record Profile(boolean enabled, String cron, int activeWindowDays,
                          int minMessagesForTone, int minInteractionsForPair,
                          int maxUsersPerRun, int maxPairsPerRun, int maxMessagesPerSummary) {}

    public record Llm(String baseUrl, String apiKey, List<String> judgmentModels, String profileModel) {}

    public record Health(int consecutiveFailureThreshold, String tag) {}

    public record ActionAnnouncement(boolean enabled, String template, boolean replyToOffendingMessage) {}
}
