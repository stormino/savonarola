package com.github.stormino.savonarola.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.health.LlmUsageTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Talks to any provider exposing an OpenAI-shaped /chat/completions — Groq, OpenRouter,
 * Mistral. Which one is live is configuration, not code.
 */
@Slf4j
public class OpenAiCompatibleClient implements LlmClient {

    private final String providerName;
    private final RestClient restClient;
    private final LlmUsageTracker usageTracker;

    public OpenAiCompatibleClient(String providerName,
                                  SavonarolaProperties.Llm.Provider provider,
                                  LlmUsageTracker usageTracker) {
        this.providerName = providerName;
        this.usageTracker = usageTracker;

        var builder = RestClient.builder()
                .baseUrl(provider.baseUrl())
                .defaultHeader("Authorization", "Bearer " + provider.apiKey());
        provider.headers().forEach(builder::defaultHeader);
        this.restClient = builder.build();
    }

    @Override
    public String complete(String model, LlmCallType callType, String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"));
        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("choices")) {
                throw new LlmException("Malformed response from " + providerName + "/" + model);
            }
            usageTracker.record(model, callType, response.path("usage"));
            return response.path("choices").get(0).path("message").path("content").asText();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(
                    "Call to " + providerName + "/" + model + " failed: " + e.getMessage(), e);
        }
    }
}
