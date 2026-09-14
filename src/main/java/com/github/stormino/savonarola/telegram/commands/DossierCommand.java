package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.group.GroupKnowledgeService;
import com.github.stormino.savonarola.telegram.AdminCommand;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import com.github.stormino.savonarola.telegram.Html;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

/**
 * SPEC 20 — what the group knows about itself. Admins seed it directly because the bot
 * cannot infer, from the text alone, that Puppo is a journalist rather than a member.
 */
@Component
@RequiredArgsConstructor
public class DossierCommand implements AdminCommand {

    private final GroupKnowledgeService knowledge;
    private final SavonarolaProperties props;
    private final AdminNotifier notifier;

    @Override
    public String name() {
        return "dossier";
    }

    @Override
    public String usage() {
        return "/dossier show | /dossier add <nota> | /dossier set <testo> | /dossier clear";
    }

    @Override
    public void handle(Message msg, List<String> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Manca il sottocomando.");
        }
        long chatId = props.telegram().mainChatId();
        long adminId = msg.getFrom().getId();
        String rest = String.join(" ", args.subList(1, args.size())).trim();

        switch (args.get(0).toLowerCase()) {
            case "show" -> show(msg, chatId);
            case "add" -> {
                requireText(rest, "Serve la nota da aggiungere.");
                knowledge.appendToDossier(chatId, rest, adminId);
                notifier.reply(msg, "Nota aggiunta:\n<i>" + Html.escape(rest) + "</i>");
            }
            case "set" -> {
                requireText(rest, "Serve il testo del dossier.");
                knowledge.replaceDossier(chatId, rest, adminId);
                notifier.reply(msg, "Dossier sostituito.");
            }
            case "clear" -> {
                knowledge.clearDossier(chatId, adminId);
                notifier.reply(msg, "Dossier svuotato. Il bot torna a giudicare senza "
                        + "contesto sul gruppo, oltre al roster.");
            }
            default -> throw new IllegalArgumentException(
                    "Sottocomando sconosciuto: '" + args.get(0) + "'.");
        }
    }

    private void show(Message msg, long chatId) {
        List<String> roster = knowledge.roster(chatId);
        String dossier = knowledge.dossier(chatId).orElse(null);

        StringBuilder sb = new StringBuilder("<b>Partecipanti riconosciuti</b> (")
                .append(roster.size()).append(")\n");
        if (roster.isEmpty()) {
            sb.append("<i>nessuno: il bot non sa ancora chi c'è nel gruppo</i>\n");
        } else {
            roster.forEach(name -> sb.append("· ").append(Html.escape(name)).append('\n'));
        }
        sb.append("\nChiunque non sia in questa lista viene trattato come un terzo di cui ")
          .append("si parla, non come un membro.\n");

        sb.append("\n<b>Dossier</b>\n");
        sb.append(dossier == null ? "<i>vuoto</i>" : Html.escape(dossier));
        notifier.reply(msg, sb.toString());
    }

    private static void requireText(String text, String complaint) {
        if (text.isBlank()) throw new IllegalArgumentException(complaint);
    }
}
