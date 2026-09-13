package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.rulebook.ProposedRule;
import com.github.stormino.savonarola.rulebook.RulebookCompiler;
import com.github.stormino.savonarola.rulebook.RulebookProposal;
import com.github.stormino.savonarola.rulebook.RulebookService;
import com.github.stormino.savonarola.rulebook.TelegramFileReader;
import com.github.stormino.savonarola.store.MessageStoreService;
import com.github.stormino.savonarola.store.StoredMessage;
import com.github.stormino.savonarola.telegram.AdminCommand;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import com.github.stormino.savonarola.telegram.Html;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.UUID;

/**
 * SPEC 2.1 — /rulebook update compiles the written rulebook into rules and posts them for
 * review. Nothing takes effect until an admin approves: a parsing error here would
 * propagate to every later decision.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RulebookCommand implements AdminCommand {

    private final RulebookCompiler compiler;
    private final RulebookService rulebook;
    private final MessageStoreService messageStore;
    private final TelegramFileReader files;
    private final SavonarolaProperties props;
    private final AdminNotifier notifier;

    @Override
    public String name() {
        return "rulebook";
    }

    @Override
    public String usage() {
        return "/rulebook update [link|testo] (o in risposta al messaggio col regolamento)"
                + " | /rulebook approve <id> | /rulebook reject <id> | /rulebook pending";
    }

    @Override
    public void handle(Message msg, List<String> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Manca il sottocomando.");
        }
        switch (args.get(0).toLowerCase()) {
            case "update" -> update(msg, args.subList(1, args.size()));
            case "approve" -> approve(msg, args.subList(1, args.size()));
            case "reject" -> reject(msg, args.subList(1, args.size()));
            case "pending" -> pending(msg);
            default -> throw new IllegalArgumentException(
                    "Sottocomando sconosciuto: '" + args.get(0) + "'.");
        }
    }

    private void update(Message msg, List<String> args) {
        String text = resolveRulebookText(msg, args);

        List<ProposedRule> compiled = compiler.compile(text);
        RulebookProposal proposal = rulebook.propose(compiled, msg.getFrom().getId());

        notifier.reply(msg, render(compiled, proposal));
    }

    /**
     * A rulebook rarely fits in one Telegram message, so the reply and link forms are the
     * ones that matter; inline text is the convenience case for a short amendment.
     */
    private String resolveRulebookText(Message msg, List<String> args) {
        Message replied = msg.getReplyToMessage();
        if (replied != null) {
            if (replied.hasDocument()) {
                return files.read(replied.getDocument().getFileId(),
                        replied.getDocument().getFileSize());
            }
            if (replied.hasText()) {
                return replied.getText();
            }
            throw new IllegalArgumentException(
                    "Il messaggio citato non contiene né testo né un file leggibile.");
        }

        if (args.isEmpty()) {
            throw new IllegalArgumentException("Serve il regolamento: cita il messaggio che lo "
                    + "contiene, allega un file, oppure passa un link o il testo.");
        }

        if (args.size() == 1) {
            var link = MessageLink.parse(args.get(0));
            if (link.isPresent()) {
                long chatId = link.get().resolveChatId(props.telegram().mainChatId());
                return messageStore.find(chatId, link.get().messageId())
                        .map(StoredMessage::getText)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Messaggio non trovato nello store: fuori retention, oppure il "
                                + "bot non era presente quando è stato inviato."));
            }
        }
        return String.join(" ", args);
    }

    private void approve(Message msg, List<String> args) {
        RulebookProposal proposal = load(args);
        var result = rulebook.approve(proposal, msg.getFrom().getId());

        notifier.reply(msg, "Regolamento aggiornato: <b>" + result.added() + "</b> regole nuove, <b>"
                + result.updated() + "</b> aggiornate, <b>" + result.disabled() + "</b> disattivate."
                + "\nLe regole disattivate non sono cancellate e i loro esempi restano.");
    }

    private void reject(Message msg, List<String> args) {
        RulebookProposal proposal = load(args);
        rulebook.reject(proposal, msg.getFrom().getId());

        notifier.reply(msg, "Proposta scartata. Il regolamento attivo non cambia.");
    }

    private void pending(Message msg) {
        List<RulebookProposal> waiting = rulebook.pending();
        if (waiting.isEmpty()) {
            notifier.reply(msg, "Nessuna proposta in attesa.");
            return;
        }
        StringBuilder sb = new StringBuilder("Proposte in attesa:\n");
        for (RulebookProposal proposal : waiting) {
            sb.append("\n<code>").append(proposal.getId()).append("</code> — ")
              .append(rulebook.read(proposal).size()).append(" regole, proposta da ")
              .append(proposal.getProposedBy());
        }
        notifier.reply(msg, sb.toString());
    }

    private RulebookProposal load(List<String> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Manca l'id della proposta.");
        }
        UUID id;
        try {
            id = UUID.fromString(args.get(0));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Id proposta non valido: '" + args.get(0) + "'.");
        }
        RulebookProposal proposal = rulebook.find(id)
                .orElseThrow(() -> new IllegalArgumentException("Nessuna proposta con id " + id + "."));
        if (proposal.getStatus() != com.github.stormino.savonarola.rulebook.ProposalStatus.PENDING) {
            throw new IllegalArgumentException("Proposta già risolta (" + proposal.getStatus() + ").");
        }
        return proposal;
    }

    private String render(List<ProposedRule> compiled, RulebookProposal proposal) {
        StringBuilder sb = new StringBuilder("📜 <b>Regolamento compilato</b> — ")
                .append(compiled.size()).append(" regole. <b>Non è ancora attivo.</b>\n");

        for (ProposedRule rule : compiled) {
            sb.append("\n<code>").append(Html.escape(rule.id())).append("</code> — ")
              .append(rule.severity())
              .append(rule.requiresHistory() ? ", richiede storico" : "")
              .append('\n').append(Html.escape(rule.definition())).append('\n');
            if (!rule.examples().isEmpty()) {
                sb.append("Esempi: ").append(rule.examples().size()).append('\n');
            }
        }

        sb.append("\nControllate le definizioni prima di approvare: un errore qui si propaga a "
                  + "tutte le decisioni successive.")
          .append("\n\n<code>/rulebook approve ").append(proposal.getId()).append("</code>")
          .append("\n<code>/rulebook reject ").append(proposal.getId()).append("</code>");
        return sb.toString();
    }
}
