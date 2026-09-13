package com.github.stormino.savonarola.config;

import com.github.stormino.savonarola.health.LlmUsageTracker;
import com.github.stormino.savonarola.llm.LlmClient;
import com.github.stormino.savonarola.llm.OpenAiCompatibleClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class LlmConfig {

    @Bean
    public LlmClient llmClient(SavonarolaProperties props, LlmUsageTracker usageTracker) {
        var provider = props.llm().active();
        if (provider.apiKey() == null || provider.apiKey().isBlank()) {
            // Otherwise the first symptom is every judgment failing hours later.
            throw new IllegalStateException("No API key for LLM provider '" + props.llm().provider()
                    + "'. Set the key for it (GROQ_API_KEY for groq, OPENROUTER_API_KEY for "
                    + "openrouter), or point savonarola.llm.provider at one you have a key for.");
        }
        log.info("LLM provider: {} ({}), judgment models {}",
                props.llm().provider(), provider.baseUrl(), provider.judgmentModels());
        return new OpenAiCompatibleClient(props.llm().provider(), provider, usageTracker);
    }
}
