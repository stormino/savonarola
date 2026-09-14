package com.github.stormino.savonarola.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.health.HealthMonitor;
import com.github.stormino.savonarola.rules.Rule;
import com.github.stormino.savonarola.store.StoredMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    public List<Violation> judge(JudgmentInput input) {
        String system = prompts.systemPrompt();
        String user = prompts.userPrompt(input);

        Set<Long> judgeable = input.candidates().stream()
                .map(StoredMessage::getMessageId).collect(Collectors.toSet());
        Set<String> knownRules = input.activeRules().stream()
                .map(Rule::getId).collect(Collectors.toSet());

        LlmException last = null;
        for (String model : props.llm().active().judgmentModels()) {
            try {
                List<Violation> violations = parse(client.complete(
                        model, LlmCallType.JUDGMENT, system, user), judgeable, knownRules);
                if (last != null) health.recordFallback();
                health.recordSuccess();
                return violations;
            } catch (LlmException e) {
                log.warn("Judgment model {} failed, trying next: {}", model, e.getMessage());
                last = e;
            }
        }
        health.recordFailure("All judgment models failed: "
                + (last != null ? last.getMessage() : "unknown"));
        throw last != null ? last : new LlmException("No judgment models configured");
    }

    /**
     * Everything the model cites is checked against what it was actually given: the message
     * ids against the window, and the rule ids against the active rulebook. A model that
     * invents either must never turn into a mute.
     */
    private List<Violation> parse(String raw, Set<Long> judgeable, Set<String> knownRules) {
        try {
            String cleaned = raw.trim()
                    .replaceAll("^```(?:json)?", "")
                    .replaceAll("```$", "")
                    .trim();
            JsonNode root = mapper.readTree(cleaned);
            JsonNode array = root.path("violations");
            if (!array.isArray()) {
                throw new LlmException("Judgment payload has no violations array");
            }

            List<Violation> violations = new ArrayList<>();
            for (JsonNode node : array) {
                long messageId = node.path("messageId").asLong(-1);
                String ruleId = node.path("ruleId").asText("").trim();

                if (!judgeable.contains(messageId)) {
                    log.warn("Judge cited message {} which was not in the window — ignored",
                            messageId);
                    continue;
                }
                if (ruleId.isBlank()) {
                    log.warn("Judge reported a violation on {} without a ruleId — ignored",
                            messageId);
                    continue;
                }
                if (!knownRules.contains(ruleId)) {
                    // Models reach for their own trained-in moderation taxonomy when the
                    // supplied rulebook does not fit: no_politics and respect_reciprocal
                    // were both invented this way in the first live session, and neither
                    // exists here. A rule we never wrote cannot sanction anyone.
                    log.warn("Judge invented rule '{}' on message {} — ignored", ruleId, messageId);
                    continue;
                }
                violations.add(new Violation(messageId, ruleId,
                        node.path("confidence").asDouble(0.0),
                        node.path("reasoning").asText("")));
            }
            return violations;
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Could not parse judgment payload: " + e.getMessage(), e);
        }
    }
}
