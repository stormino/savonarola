package com.github.stormino.savonarola.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.health.HealthMonitor;
import com.github.stormino.savonarola.moderation.Judgment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelChainJudge implements LlmJudge {

    private final LlmClient client;
    private final JudgmentPromptBuilder prompts;
    private final SavonarolaProperties props;
    private final HealthMonitor health;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Judgment judge(JudgmentInput input) {
        String system = prompts.systemPrompt();
        String user = prompts.userPrompt(input);

        LlmException last = null;
        for (String model : props.llm().active().judgmentModels()) {
            try {
                String raw = client.complete(model, LlmCallType.JUDGMENT, system, user);
                Judgment judgment = parse(raw);
                if (last != null) health.recordFallback();
                health.recordSuccess();
                return judgment;
            } catch (LlmException e) {
                log.warn("Judgment model {} failed, trying next: {}", model, e.getMessage());
                last = e;
            }
        }
        health.recordFailure("All judgment models failed: "
                + (last != null ? last.getMessage() : "unknown"));
        throw last != null ? last : new LlmException("No judgment models configured");
    }

    private Judgment parse(String raw) {
        try {
            String cleaned = raw.trim()
                    .replaceAll("^```(?:json)?", "")
                    .replaceAll("```$", "")
                    .trim();
            JsonNode node = mapper.readTree(cleaned);
            boolean violated = node.path("violated").asBoolean(false);
            String ruleId = node.path("ruleId").isNull() ? null : node.path("ruleId").asText(null);
            double confidence = node.path("confidence").asDouble(0.0);
            String reasoning = node.path("reasoning").asText("");
            if (violated && (ruleId == null || ruleId.isBlank())) {
                throw new LlmException("Model reported a violation without a ruleId");
            }
            return new Judgment(violated, ruleId, confidence, reasoning);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Could not parse judgment payload: " + e.getMessage(), e);
        }
    }
}
