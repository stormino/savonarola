package com.github.stormino.savonarola.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmProviderSelectionTest {

    private static SavonarolaProperties.Llm.Provider provider(String baseUrl) {
        return new SavonarolaProperties.Llm.Provider(baseUrl, "key", null,
                List.of("primary"), "profile");
    }

    private static SavonarolaProperties.Llm llm(String active) {
        return new SavonarolaProperties.Llm(active, Map.of(
                "groq", provider("https://api.groq.com/openai/v1"),
                "openrouter", provider("https://openrouter.ai/api/v1")));
    }

    @Test
    void selectsOnlyTheActiveProvider() {
        assertThat(llm("groq").active().baseUrl()).isEqualTo("https://api.groq.com/openai/v1");
        assertThat(llm("openrouter").active().baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
    }

    @Test
    void failsLoudlyAndNamesWhatIsConfigured() {
        assertThatThrownBy(() -> llm("gemini").active())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemini")
                .hasMessageContaining("groq");
    }

    @Test
    void saysSoWhenNothingIsConfiguredAtAll() {
        assertThatThrownBy(() -> new SavonarolaProperties.Llm("groq", null).active())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("none");
    }

    @Test
    void refusesToStartWithoutAKeyForTheActiveProvider() {
        var props = com.github.stormino.savonarola.TestProperties.withLlm(
                new SavonarolaProperties.Llm("groq", Map.of("groq",
                        new SavonarolaProperties.Llm.Provider("http://x", "  ", null,
                                List.of("m"), "p"))));

        assertThatThrownBy(() -> new LlmConfig().llmClient(props, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GROQ_API_KEY");
    }

    @Test
    void treatsAbsentHeadersAsNoHeaders() {
        assertThat(provider("http://x").headers()).isEmpty();
        assertThat(new SavonarolaProperties.Llm.Provider("http://x", "k",
                Map.of("X-Title", "Savonarola"), List.of("m"), "p").headers())
                .containsEntry("X-Title", "Savonarola");
    }
}
