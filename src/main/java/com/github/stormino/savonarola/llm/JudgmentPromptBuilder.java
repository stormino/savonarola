package com.github.stormino.savonarola.llm;

import com.github.stormino.savonarola.rules.RuleExample;
import com.github.stormino.savonarola.store.StoredMessage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JudgmentPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a moderation assistant for an Italian tennis-fan Telegram group.
            You will be given the group's rulebook, a recent conversation window, and a
            batch of messages to judge. Messages are in Italian.

            PARTICIPANTS: you will be given the list of people who are actually in this
            group. Everyone else named in the conversation — players, coaches, umpires,
            journalists, commentators, any public figure — is a THIRD PARTY being
            discussed, not a participant. Rules that protect participants never apply to
            a third party, however harshly the group speaks about them. Mocking a pundit
            is not mockery of a participant; calling a player names is not an insult to
            anyone in the room. If you cannot identify the target as someone on the
            participants list, it is not a violation of a rule that requires a target.

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

            OUTPUT: respond with a single JSON object listing ONLY the messages that
            violate a rule:
            {"violations": [{"messageId": number, "ruleId": string,
                             "confidence": number 0-1, "reasoning": string}]}

            Return {"violations": []} when nothing breaks a rule. That is the common and
            correct answer for ordinary conversation, however heated it gets.

            Cite only ids that appear under MESSAGES TO JUDGE, and judge only those
            messages — the conversation above them is context, not something to rule on.
            "reasoning" must be written in Italian, one or two sentences, readable by a
            human admin deciding whether to confirm a penalty. If uncertain, prefer a
            lower confidence over a forced call.
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String userPrompt(JudgmentInput in) {
        StringBuilder sb = new StringBuilder();

        sb.append("## PARTICIPANTS IN THIS GROUP\n");
        if (in.participants().isEmpty()) {
            sb.append("(unknown — treat every named person as possibly a third party)\n");
        } else {
            in.participants().forEach(name -> sb.append("- ").append(name).append('\n'));
            sb.append("Anyone named who is NOT on this list is a third party being ")
              .append("discussed, not a member of this group.\n");
        }

        if (in.groupDossier() != null && !in.groupDossier().isBlank()) {
            sb.append("\n## ABOUT THIS GROUP\n").append(in.groupDossier()).append('\n');
        }

        sb.append("\n## RULEBOOK\n");
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

        if (!in.profilesBySender().isEmpty()) {
            sb.append("\n## SENDER PROFILES\n");
            in.profilesBySender().forEach((userId, profile) ->
                    sb.append("- user ").append(userId).append(": ").append(profile).append('\n'));
        }

        // The candidates are also the tail of the chat, so drop them from the context
        // block rather than showing every message twice.
        Set<Long> candidateIds = in.candidates().stream()
                .map(StoredMessage::getMessageId).collect(Collectors.toSet());
        List<StoredMessage> context = in.contextWindow().stream()
                .filter(m -> !candidateIds.contains(m.getMessageId()))
                .sorted(Comparator.comparing(StoredMessage::getSentAt))
                .toList();

        if (!context.isEmpty()) {
            sb.append("\n## EARLIER CONVERSATION (context only, do not judge)\n");
            context.forEach(m -> sb.append(format(m)).append('\n'));
        }

        if (in.extendedHistory() != null && !in.extendedHistory().isEmpty()) {
            sb.append("\n## PRIOR INTERACTIONS BETWEEN PARTICIPANTS IN THIS WINDOW\n");
            sb.append("(provided because a possible pattern was detected; judge whether it is ")
              .append("a recurring targeted pattern or ordinary recurring banter)\n");
            in.extendedHistory().stream()
                    .sorted(Comparator.comparing(StoredMessage::getSentAt))
                    .forEach(m -> sb.append(format(m)).append('\n'));
        }

        sb.append("\n## MESSAGES TO JUDGE\n");
        in.candidates().stream()
                .sorted(Comparator.comparing(StoredMessage::getSentAt))
                .forEach(m -> sb.append("[id ").append(m.getMessageId()).append("] ")
                        .append(m.getSenderName()).append(": ").append(m.getText()).append('\n'));
        return sb.toString();
    }

    private static String format(StoredMessage m) {
        return "[" + m.getSentAt() + "] " + m.getSenderName() + ": " + m.getText();
    }
}
