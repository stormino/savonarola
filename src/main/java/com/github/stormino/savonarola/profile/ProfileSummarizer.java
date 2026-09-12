package com.github.stormino.savonarola.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.llm.LlmClient;
import com.github.stormino.savonarola.llm.LlmException;
import com.github.stormino.savonarola.store.StoredMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Batch profiling runs on the cheap model: it is a prior, not a verdict, and a wrong
 * tone summary must never be expensive. Summaries are written in English, like rule
 * definitions, because they are prompt scaffolding rather than content anyone reads.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProfileSummarizer {

    private static final String TONE_SYSTEM = """
            You profile how a member of an Italian tennis-fan Telegram group habitually
            writes, so a moderation judge can tell an unusual message from a normal one.

            Describe register and habit only: how blunt, sardonic, warm, or terse they
            usually are, and whether harshness is their baseline. Never summarise their
            opinions, never judge them, and never say whether anything broke a rule.
            Messages are in Italian; write the summary in English, one or two sentences.

            Respond with a single JSON object and nothing else:
            {"typicalTone": string}
            """;

    private static final String PAIR_SYSTEM = """
            You assess how one member of an Italian tennis-fan Telegram group behaves
            toward one other member, from messages the first sent in reply to the second.

            Count how many are hostile toward that person: insults, ridicule of them,
            veiled digs, or pressure aimed at them. Blunt disagreement about tennis,
            players, or matches is NOT hostile however harsh it reads, and neither is
            mutual joking that both clearly take part in.

            If the exchange shows an established pattern worth remembering — a long-running
            friendly rivalry, or persistent one-sided needling — describe it in one English
            sentence. Otherwise leave the note null.

            Respond with a single JSON object and nothing else:
            {"negativeInteractions": integer, "note": string|null}
            """;

    private final LlmClient client;
    private final SavonarolaProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<String> summarizeTone(List<StoredMessage> messages) {
        String prompt = "## MESSAGES FROM THIS PERSON (oldest first)\n" + transcript(messages);
        return call(TONE_SYSTEM, prompt)
                .map(node -> node.path("typicalTone").asText(null))
                .filter(tone -> tone != null && !tone.isBlank());
    }

    public Optional<PairAssessment> assessPair(String senderName, String targetName,
                                               List<StoredMessage> exchange) {
        String prompt = "## MESSAGES FROM " + senderName + " IN REPLY TO " + targetName
                + " (oldest first)\n" + transcript(exchange);
        return call(PAIR_SYSTEM, prompt).map(node -> new PairAssessment(
                Math.max(0, node.path("negativeInteractions").asInt(0)),
                node.path("note").isNull() ? null : node.path("note").asText(null)));
    }

    private Optional<JsonNode> call(String system, String userPrompt) {
        try {
            String raw = client.complete(props.llm().profileModel(), system, userPrompt);
            String cleaned = raw.trim()
                    .replaceAll("^```(?:json)?", "")
                    .replaceAll("```$", "")
                    .trim();
            return Optional.of(mapper.readTree(cleaned));
        } catch (LlmException e) {
            log.warn("Profile call failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Could not parse profile payload: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String transcript(List<StoredMessage> messages) {
        StringBuilder sb = new StringBuilder();
        messages.stream()
                .sorted(Comparator.comparing(StoredMessage::getSentAt))
                .forEach(m -> sb.append("- ").append(m.getText()).append('\n'));
        return sb.toString();
    }

    public record PairAssessment(int negativeInteractions, String note) {}
}
