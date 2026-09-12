package com.github.stormino.savonarola.rulebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.llm.LlmClient;
import com.github.stormino.savonarola.llm.LlmException;
import com.github.stormino.savonarola.moderation.Severity;
import com.github.stormino.savonarola.rules.ExampleLabel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SPEC 2.1 — turns the written rulebook into discrete rules.
 *
 * Runs on the judgment models, not the cheap one: a parsing error here propagates to
 * every later decision, which is also why nothing it produces takes effect without an
 * admin approving it.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RulebookCompiler {

    private static final String SYSTEM = """
            You convert the written rulebook of an Italian tennis-fan Telegram group into
            discrete, machine-checkable rules for a moderation judge.

            For each rule in the text produce:
            - id: english_snake_case, stable and descriptive
            - severity: LOW, MEDIUM or HIGH
            - requiresHistory: true when a single message cannot establish the violation
              and prior interactions between the same two people are needed
            - definition: English, precise about where the boundary lies. State what does
              NOT cross it as explicitly as what does. This group's rulebook explicitly
              welcomes harsh disagreement about tennis, so a definition that would catch
              blunt criticism is wrong.
            - examples: any illustrative messages the text itself gives, kept in Italian,
              labelled POSITIVE when they violate the rule and NEGATIVE when they are
              close to the line but allowed. Empty list if the text gives none.

            Do not invent rules the text does not contain, and do not merge two distinct
            rules into one. If the text is vague about a boundary, say so in the
            definition rather than guessing a sharp line.

            Respond with a single JSON object and nothing else:
            {"rules": [{"id": string, "severity": string, "requiresHistory": boolean,
                        "definition": string,
                        "examples": [{"text": string, "label": "POSITIVE"|"NEGATIVE"}]}]}
            """;

    private final LlmClient client;
    private final SavonarolaProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<ProposedRule> compile(String rulebookText) {
        String prompt = "## RULEBOOK (Italian)\n" + rulebookText;

        LlmException last = null;
        for (String model : props.llm().judgmentModels()) {
            try {
                return parse(client.complete(model, SYSTEM, prompt));
            } catch (LlmException e) {
                log.warn("Rulebook compilation on {} failed: {}", model, e.getMessage());
                last = e;
            }
        }
        throw last != null ? last : new LlmException("No judgment models configured");
    }

    private List<ProposedRule> parse(String raw) {
        try {
            String cleaned = raw.trim()
                    .replaceAll("^```(?:json)?", "")
                    .replaceAll("```$", "")
                    .trim();
            JsonNode root = mapper.readTree(cleaned);

            List<ProposedRule> rules = new ArrayList<>();
            for (JsonNode node : root.path("rules")) {
                String id = node.path("id").asText("").trim();
                String definition = node.path("definition").asText("").trim();
                if (id.isBlank() || definition.isBlank()) {
                    throw new LlmException("A rule came back without an id or a definition");
                }
                rules.add(new ProposedRule(
                        id,
                        Severity.valueOf(node.path("severity").asText("MEDIUM").toUpperCase()),
                        node.path("requiresHistory").asBoolean(false),
                        definition,
                        examples(node.path("examples"))));
            }
            if (rules.isEmpty()) {
                throw new LlmException("No rules found in that text");
            }
            return rules;
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Could not parse the compiled rulebook: " + e.getMessage(), e);
        }
    }

    private static List<ProposedRule.ProposedExample> examples(JsonNode node) {
        List<ProposedRule.ProposedExample> examples = new ArrayList<>();
        for (JsonNode example : node) {
            String text = example.path("text").asText("").trim();
            if (text.isBlank()) continue;
            examples.add(new ProposedRule.ProposedExample(text,
                    ExampleLabel.valueOf(example.path("label").asText("POSITIVE").toUpperCase())));
        }
        return examples;
    }
}
