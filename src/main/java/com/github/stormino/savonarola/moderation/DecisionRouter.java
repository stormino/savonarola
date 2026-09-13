package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.settings.SettingsService;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import com.github.stormino.savonarola.telegram.Html;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/** Applies operating mode and the confidence gate to a raw judgment. */
@Service
@Slf4j
@RequiredArgsConstructor
public class DecisionRouter {

    private final SavonarolaProperties props;
    private final SettingsService settings;
    private final DecisionRepository decisions;
    private final AdminNotifier notifier;
    private final ActionExecutor executor;

    public void route(Message msg, Judgment judgment, Action suggested) {
        OperatingMode mode = settings.operatingMode();

        if (mode == OperatingMode.LOG_ONLY) {
            if (judgment.violated()) {
                decisions.save(Decision.logged(msg.getChatId(), msg.getMessageId(),
                        msg.getFrom().getId(), judgment, suggested));
            }
            notifier.send(dryRunReport(msg, judgment, suggested));
            return;
        }

        if (!judgment.violated()) return;

        if (judgment.confidence() < settings.confidenceThreshold()) {
            // Not confident enough to propose a penalty — a human decides from scratch.
            notifier.send(lowConfidenceFlag(msg, judgment));
            return;
        }

        Decision decision = Decision.pending(
                msg.getChatId(), msg.getMessageId(), msg.getFrom().getId(), judgment, suggested);
        decisions.save(decision);

        if (suggested.type() == ActionType.ADMIN_REVIEW) {
            notifier.send(adminReviewRequest(msg, judgment, decision));
            return;
        }

        if (mode == OperatingMode.ON_DEMAND_ACTION) {
            notifier.send(proposal(msg, judgment, decision, suggested));
            return;
        }

        executeAutonomously(msg, decision, suggested);
    }

    private void executeAutonomously(Message msg, Decision decision, Action suggested) {
        try {
            executor.mute(msg.getChatId(), msg.getFrom().getId(), displayName(msg),
                    msg.getMessageId(), suggested.durationMinutes());
            decision.markExecuted(suggested, null);
            decisions.save(decision);
            notifier.send("⚖️ <b>Azione eseguita</b> — " + Html.escape(displayName(msg))
                    + " mutato per " + suggested.durationMinutes() + " min"
                    + "\nRegola: <code>" + Html.escape(decision.getRuleId()) + "</code>"
                    + " (confidence " + format(decision.getConfidence()) + ")"
                    + "\n" + Html.escape(decision.getReasoning()));
        } catch (Exception e) {
            log.error("Autonomous action failed for decision {}", decision.getId(), e);
            notifier.sendSystem("Failed to execute decision " + decision.getId() + ": " + e.getMessage());
        }
    }

    private String dryRunReport(Message msg, Judgment judgment, Action suggested) {
        String verdict = judgment.violated()
                ? "VIOLAZIONE <code>" + Html.escape(judgment.ruleId()) + "</code>"
                : "nessuna violazione";
        return "🧪 <b>[DRY RUN]</b> " + verdict
                + " — confidence " + format(judgment.confidence())
                + "\nDa: " + Html.escape(displayName(msg))
                + "\nMessaggio: <i>" + Html.escape(msg.getText()) + "</i>"
                + "\nMotivazione: " + Html.escape(judgment.reasoning())
                + (suggested != null ? "\nAvrebbe proposto: " + describe(suggested) : "");
    }

    private String lowConfidenceFlag(Message msg, Judgment judgment) {
        return "❓ <b>Segnalazione a bassa confidenza</b> — <code>" + Html.escape(judgment.ruleId()) + "</code>"
                + " (confidence " + format(judgment.confidence()) + ", sotto soglia)"
                + "\nDa: " + Html.escape(displayName(msg))
                + "\nMessaggio: <i>" + Html.escape(msg.getText()) + "</i>"
                + "\nMotivazione: " + Html.escape(judgment.reasoning())
                + "\nNessuna azione proposta: valutate voi.";
    }

    private String proposal(Message msg, Judgment judgment, Decision decision, Action suggested) {
        return "⚖️ <b>Decisione in attesa</b> — <code>" + Html.escape(judgment.ruleId()) + "</code>"
                + " (confidence " + format(judgment.confidence()) + ")"
                + "\nDa: " + Html.escape(displayName(msg))
                + "\nMessaggio: <i>" + Html.escape(msg.getText()) + "</i>"
                + "\nMotivazione: " + Html.escape(judgment.reasoning())
                + "\nProposta: " + describe(suggested)
                + "\n\n<code>/execute " + decision.getId() + "</code>"
                + "\n<code>/execute " + decision.getId() + " duration=10m</code>"
                + "\n<code>/execute " + decision.getId() + " dismiss</code>";
    }

    private String adminReviewRequest(Message msg, Judgment judgment, Decision decision) {
        return "🛑 <b>Fine della scala di escalation</b> — <code>" + Html.escape(judgment.ruleId()) + "</code>"
                + "\nDa: " + Html.escape(displayName(msg))
                + "\nMessaggio: <i>" + Html.escape(msg.getText()) + "</i>"
                + "\nMotivazione: " + Html.escape(judgment.reasoning())
                + "\nIl bot non procede oltre: serve una decisione umana."
                + "\nPer applicare comunque un mute: <code>/execute "
                + decision.getId() + " duration=24h</code>";
    }

    private static String describe(Action action) {
        return action.type() == ActionType.ADMIN_REVIEW
                ? "revisione admin (gradino " + action.rung() + ")"
                : "mute " + action.durationMinutes() + " min (gradino " + action.rung() + ")";
    }

    private static String displayName(Message msg) {
        var user = msg.getFrom();
        if (user.getUserName() != null) return "@" + user.getUserName();
        return user.getFirstName();
    }

    private static String format(double confidence) {
        return String.format("%.2f", confidence);
    }
}
