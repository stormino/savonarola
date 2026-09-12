package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.moderation.Action;
import com.github.stormino.savonarola.moderation.ActionExecutor;
import com.github.stormino.savonarola.moderation.ActionType;
import com.github.stormino.savonarola.moderation.Decision;
import com.github.stormino.savonarola.moderation.DecisionRepository;
import com.github.stormino.savonarola.moderation.DecisionStatus;
import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.store.MessageStoreService;
import com.github.stormino.savonarola.telegram.AdminCommand;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import com.github.stormino.savonarola.telegram.AdminRegistry;
import com.github.stormino.savonarola.telegram.Html;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.UUID;

/**
 * SPEC section 10 — an admin confirms, adjusts, or discards a pending decision.
 *
 * The command is refused outright in LOG_ONLY: that mode promises no action is possible,
 * not even manually. It is accepted in LIVE_ACTION as well as ON_DEMAND_ACTION, because
 * decisions that reach the top of the ladder are parked as PENDING in both modes and
 * would otherwise be unreachable.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExecuteCommand implements AdminCommand {

    private final SavonarolaProperties props;
    private final DecisionRepository decisions;
    private final ActionExecutor executor;
    private final MessageStoreService messageStore;
    private final AdminRegistry adminRegistry;
    private final AdminNotifier notifier;

    @Override
    public String name() {
        return "execute";
    }

    @Override
    public String usage() {
        return "/execute <decisionId> [duration=<Nm|Nh|Nd> | dismiss]";
    }

    @Override
    public void handle(Message msg, List<String> args) {
        if (props.operatingMode() == OperatingMode.LOG_ONLY) {
            notifier.reply(msg, "Modalità <b>LOG_ONLY</b>: nessuna azione è eseguibile, "
                    + "nemmeno manualmente. Cambia modalità operativa per usare /execute.");
            return;
        }
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Manca l'id della decisione.");
        }

        Decision decision = decisions.findById(parseId(args.get(0)))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nessuna decisione con id " + args.get(0) + "."));

        if (decision.getStatus() != DecisionStatus.PENDING) {
            notifier.reply(msg, "Decisione già risolta (<b>" + decision.getStatus() + "</b>"
                    + (decision.getResolvedBy() != null ? ", da " + decision.getResolvedBy() : "")
                    + "). Nessuna azione ripetuta.");
            return;
        }

        long adminId = msg.getFrom().getId();
        String modifier = args.size() > 1 ? args.get(1).toLowerCase() : "";

        if (modifier.equals("dismiss")) {
            decision.markDismissed(adminId);
            decisions.save(decision);
            notifier.reply(msg, "Decisione <code>" + decision.getId() + "</code> scartata. "
                    + "Nessuna azione sull'utente.");
            return;
        }

        Action action = resolveAction(decision, modifier);

        // The subject may have been promoted since the judgment: admins are never sanctionable.
        if (adminRegistry.isAdmin(decision.getChatId(), decision.getSubjectUserId())) {
            notifier.reply(msg, "L'utente è ora amministratore del gruppo: nessuna azione possibile.");
            return;
        }

        String displayName = messageStore.find(decision.getChatId(), decision.getMessageId())
                .map(m -> m.getSenderName())
                .orElse("l'utente");

        try {
            executor.mute(decision.getChatId(), decision.getSubjectUserId(), displayName,
                    decision.getMessageId(), action.durationMinutes());
        } catch (Exception e) {
            log.error("Failed to execute decision {}", decision.getId(), e);
            notifier.reply(msg, "Esecuzione fallita: " + Html.escape(e.getMessage())
                    + "\nLa decisione resta in attesa.");
            return;
        }

        decision.markExecuted(action, adminId);
        decisions.save(decision);

        notifier.reply(msg, "Eseguito: " + Html.escape(displayName)
                + " mutato per " + action.durationMinutes() + " min"
                + (decision.wasModified()
                        ? " (modificato rispetto ai " + decision.suggestedAction().durationMinutes()
                          + " min proposti)"
                        : "")
                + ".");
    }

    /**
     * Without an explicit duration the bot's own proposal is applied — except at the top
     * of the ladder, where there is no proposal by design (SPEC section 9) and the admin
     * has to state what they want.
     */
    private Action resolveAction(Decision decision, String modifier) {
        Action suggested = decision.suggestedAction();

        if (modifier.startsWith("duration=")) {
            int minutes = DurationParser.toMinutes(modifier.substring("duration=".length()));
            return Action.mute(minutes, suggested.rung());
        }
        if (!modifier.isEmpty()) {
            throw new IllegalArgumentException("Argomento non riconosciuto: '" + modifier + "'.");
        }
        if (suggested.type() == ActionType.ADMIN_REVIEW) {
            throw new IllegalArgumentException(
                    "Questa decisione è al tetto della scala: il bot non propone una durata. "
                    + "Indicala esplicitamente, per esempio duration=24h.");
        }
        return suggested;
    }

    private static UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Id decisione non valido: '" + raw + "'.");
        }
    }
}
