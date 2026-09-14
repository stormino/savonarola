package com.github.stormino.savonarola.config;

import com.github.stormino.savonarola.moderation.OperatingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "savonarola")
public record SavonarolaProperties(
        OperatingMode operatingMode,
        Telegram telegram,
        Decision decision,
        PatternDetection patternDetection,
        Escalation escalation,
        MessageStore messageStore,
        RuleSet ruleSet,
        Llm llm,
        Profile profile,
        Health health,
        ActionAnnouncement actionAnnouncement
) {
    /** ownerChatId is the verbose stream; 0 disables it. The admin chat never sees it. */
    public record Telegram(long mainChatId, long adminChatId, long ownerChatId, String token) {

        public boolean hasOwnerChat() {
            return ownerChatId != 0;
        }
    }

    public record Decision(double confidenceThreshold) {}

    public record PatternDetection(int negativeInteractionThreshold, int windowDays) {}

    /** Ladder entries are minutes; the terminal rung is ADMIN_REVIEW (no auto action). */
    public record Escalation(List<Integer> ladderMinutes, int decayAfterDays) {}

    public record MessageStore(int retentionDays, int contextWindowSize) {}

    /** Caps what reaches the prompt, not what is stored: every example is sent every time. */
    public record RuleSet(int maxExamplesPerRule) {}

    /** Caps exist because every user and every pair costs one call against a shared budget. */
    public record Profile(boolean enabled, String cron, int activeWindowDays,
                          int minMessagesForTone, int minInteractionsForPair,
                          int maxUsersPerRun, int maxPairsPerRun, int maxMessagesPerSummary) {}

    /**
     * Providers are configured together and one is active. Groq, OpenRouter and Mistral are
     * all OpenAI-shaped, so they differ only by base URL, key and headers — a provider that
     * is not (Gemini) needs its own LlmClient rather than an entry here.
     */
    public record Llm(String provider, Map<String, Provider> providers) {

        public record Provider(String baseUrl, String apiKey, Map<String, String> headers,
                               List<String> judgmentModels, String profileModel) {

            public Map<String, String> headers() {
                return headers == null ? Map.of() : headers;
            }
        }

        public Provider active() {
            Provider selected = providers == null ? null : providers.get(provider);
            if (selected == null) {
                throw new IllegalStateException("No configuration for LLM provider '" + provider
                        + "'. Configured: " + (providers == null ? "none" : providers.keySet()));
            }
            return selected;
        }
    }

    public record Health(int consecutiveFailureThreshold, String tag) {}

    public record ActionAnnouncement(boolean enabled, String template, boolean replyToOffendingMessage) {}
}
