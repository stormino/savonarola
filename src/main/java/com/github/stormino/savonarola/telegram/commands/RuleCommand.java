package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.rules.Rule;
import com.github.stormino.savonarola.rules.RuleRepository;
import com.github.stormino.savonarola.telegram.AdminCommand;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import com.github.stormino.savonarola.telegram.Html;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RuleCommand implements AdminCommand {

    private final RuleRepository rules;
    private final AdminNotifier notifier;

    @Override
    public String name() {
        return "rule";
    }

    @Override
    public String usage() {
        return "/rule list | /rule enable <rule_id> | /rule disable <rule_id>";
    }

    @Override
    @Transactional
    public void handle(Message msg, List<String> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Manca il sottocomando.");
        }
        switch (args.get(0).toLowerCase()) {
            case "list" -> list(msg);
            case "enable" -> toggle(msg, args, true);
            case "disable" -> toggle(msg, args, false);
            default -> throw new IllegalArgumentException(
                    "Sottocomando sconosciuto: '" + args.get(0) + "'.");
        }
    }

    private void list(Message msg) {
        List<Rule> all = rules.findAll().stream()
                .sorted(Comparator.comparing(Rule::getId))
                .toList();
        if (all.isEmpty()) {
            notifier.reply(msg, "Nessuna regola: il bot non può giudicare nulla.");
            return;
        }
        StringBuilder sb = new StringBuilder("Regole:\n");
        for (Rule rule : all) {
            sb.append(rule.isEnabled() ? "\n✅ " : "\n⛔ ")
              .append("<code>").append(Html.escape(rule.getId())).append("</code> — ")
              .append(rule.getSeverity())
              .append(rule.isRequiresHistory() ? ", richiede storico" : "");
        }
        notifier.reply(msg, sb.toString());
    }

    private void toggle(Message msg, List<String> args, boolean enabled) {
        if (args.size() < 2) {
            throw new IllegalArgumentException("Manca l'id della regola.");
        }
        String ruleId = args.get(1);
        Rule rule = rules.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Regola sconosciuta: '" + ruleId + "'."));

        if (rule.isEnabled() == enabled) {
            notifier.reply(msg, "La regola <code>" + Html.escape(ruleId) + "</code> è già "
                    + (enabled ? "attiva" : "disattiva") + ".");
            return;
        }
        rule.setEnabled(enabled);

        notifier.send("⚙️ Regola <code>" + Html.escape(ruleId) + "</code> "
                + (enabled ? "<b>attivata</b>" : "<b>disattivata</b>")
                + "\nDa: " + msg.getFrom().getId()
                + (enabled ? "" : "\nGli esempi restano: la regola è spenta, non cancellata."));
    }
}
