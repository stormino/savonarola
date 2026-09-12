package com.github.stormino.savonarola.llm;

import com.github.stormino.savonarola.rules.RuleExample;
import com.github.stormino.savonarola.store.StoredMessage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class JudgmentPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a moderation assistant for an Italian tennis-fan Telegram group.
            You will be given the group's rulebook, a recent conversation window, and
            the message to judge. Messages are in Italian.

            RULES: interpret literally — a message violates a rule only if it clearly
            matches its definition. Do not infer intent beyond what the text and
            context support. Passionate, harsh, or blunt disagreement about tennis
            opinions is explicitly ALLOWED and must never be flagged on its own.

            CONTEXT: use the sender's typical tone profile and any known dynamic with
            the recipient to interpret ambiguous tone. Established banter/rivalry
            patterns are NOT violations even if the words alone would look harsh out
            of context. However, a clear violation is a violation regardless of the
            sender's usual style — the profile disambiguates tone, it does not excuse
            crossing a line.

            OUTPUT: respond with a single JSON object and nothing else:
            {"violated": boolean, "ruleId": string|null, "confidence": number 0-1, "reasoning": string}
            "reasoning" must be written in Italian, one or two sentences, readable by a
            human admin deciding whether to confirm a penalty. Always fill it in, even
            when violated is false. If uncertain, prefer lower confidence over a forced
            binary call.
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String userPrompt(JudgmentInput in) {
        StringBuilder sb = new StringBuilder();

        sb.append("## RULEBOOK\n");
        for (var rule : in.activeRules()) {
            sb.append("\n### ").append(rule.getId())
              .append(" (severity: ").append(rule.getSeverity()).append(")\n")
              .append(rule.getDefinition()).append('\n');

            List<RuleExample> examples = in.examplesByRule().getOrDefault(rule.getId(), List.of());
            if (!examples.isEmpty()) {
                sb.append("Examples:\n");
                for (var ex : examples) {
                    sb.append("- [").append(ex.getLabel()).append("] ")
                      .append(ex.getText()).append('\n');
                }
            }
        }

        if (in.senderProfile() != null) {
            sb.append("\n## SENDER PROFILE\n").append(in.senderProfile()).append('\n');
        }
        if (in.targetProfile() != null) {
            sb.append("\n## RECIPIENT PROFILE\n").append(in.targetProfile()).append('\n');
        }

        sb.append("\n## RECENT CONVERSATION (oldest first)\n");
        in.contextWindow().stream()
                .sorted(Comparator.comparing(StoredMessage::getSentAt))
                .forEach(m -> sb.append(format(m)).append('\n'));

        if (in.extendedHistory() != null && !in.extendedHistory().isEmpty()) {
            sb.append("\n## PRIOR INTERACTIONS FROM SENDER TOWARD THIS RECIPIENT\n");
            sb.append("(provided because a possible pattern was detected; judge whether it is ")
              .append("a recurring targeted pattern or ordinary recurring banter)\n");
            in.extendedHistory().stream()
                    .sorted(Comparator.comparing(StoredMessage::getSentAt))
                    .forEach(m -> sb.append(format(m)).append('\n'));
        }

        sb.append("\n## MESSAGE TO JUDGE\n").append(format(in.targetMessage())).append('\n');
        return sb.toString();
    }

    private static String format(StoredMessage m) {
        return "[" + m.getSentAt() + "] " + m.getSenderName() + ": " + m.getText();
    }
}
