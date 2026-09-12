package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.rules.ExampleLabel;
import com.github.stormino.savonarola.rules.RuleExample;
import com.github.stormino.savonarola.rules.RuleSetService;
import com.github.stormino.savonarola.store.MessageStoreService;
import com.github.stormino.savonarola.store.StoredMessage;
import com.github.stormino.savonarola.telegram.AdminCommand;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import com.github.stormino.savonarola.telegram.Html;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.Optional;

/**
 * SPEC section 8 — /train &lt;rule_id&gt; &lt;positive|negative&gt; &lt;message_link&gt;
 *
 * Labelling a message never sanctions it, however clear the violation: training enriches
 * the rulebook for future judgments and nothing else.
 */
@Component
@RequiredArgsConstructor
public class TrainCommand implements AdminCommand {

    private final RuleSetService ruleSet;
    private final MessageStoreService messageStore;
    private final SavonarolaProperties props;
    private final AdminNotifier notifier;

    @Override
    public String name() {
        return "train";
    }

    @Override
    public String usage() {
        return "/train <rule_id> <positive|negative> <message_link>";
    }

    @Override
    public void handle(Message msg, List<String> args) {
        if (args.size() < 3) {
            throw new IllegalArgumentException("Servono regola, etichetta e link al messaggio.");
        }

        String ruleId = args.get(0);
        if (!ruleSet.exists(ruleId)) {
            throw new IllegalArgumentException("Regola sconosciuta: '" + ruleId + "'.");
        }

        ExampleLabel label = parseLabel(args.get(1));

        MessageLink link = MessageLink.parse(args.get(2))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Link non riconosciuto. Usa il link 'Copia link messaggio' di Telegram."));

        long chatId = link.resolveChatId(props.telegram().mainChatId());
        Optional<StoredMessage> stored = messageStore.find(chatId, link.messageId());

        if (stored.isEmpty()) {
            notifier.reply(msg, "Messaggio non trovato nello store: è fuori dalla finestra di "
                    + "retention (" + props.messageStore().retentionDays()
                    + " giorni) oppure il bot non era presente quando è stato inviato.");
            return;
        }

        StoredMessage source = stored.get();
        ruleSet.addExample(new RuleExample(ruleId, source.getText(), label,
                msg.getFrom().getId(), chatId, link.messageId()));

        notifier.reply(msg, "Esempio <b>" + label + "</b> aggiunto a <code>" + Html.escape(ruleId)
                + "</code>:\n<i>" + Html.escape(source.getText()) + "</i>"
                + "\n\nNessuna azione retroattiva: vale solo per i giudizi futuri.");
    }

    private static ExampleLabel parseLabel(String raw) {
        try {
            return ExampleLabel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Etichetta non valida: '" + raw + "'. Usa positive o negative.");
        }
    }
}
