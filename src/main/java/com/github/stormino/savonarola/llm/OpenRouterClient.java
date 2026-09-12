package com.github.stormino.savonarola.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.health.LlmUsageTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@Slf4j
public class OpenRouterClient implements LlmClient {

    private final RestClient restClient;
    private final LlmUsageTracker usageTracker;

    public OpenRouterClient(SavonarolaProperties props, LlmUsageTracker usageTracker) {
        this.usageTracker = usageTracker;
        this.restClient = RestClient.builder()
                .baseUrl(props.llm().baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.llm().apiKey())
                .defaultHeader("X-Title", "savonarola")
                .build();
    }

    @Override
    public String complete(String model, String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", java.util.List.of(
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
                throw new LlmException("Malformed response from model " + model);
            }
            usageTracker.record(model, response.path("usage"));
            return response.path("choices").get(0).path("message").path("content").asText();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Call to model " + model + " failed: " + e.getMessage(), e);
        }
    }
}
