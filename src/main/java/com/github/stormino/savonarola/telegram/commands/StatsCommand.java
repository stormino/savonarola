package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.health.HealthMonitor;
import com.github.stormino.savonarola.health.LlmUsageTracker;
import com.github.stormino.savonarola.moderation.Decision;
import com.github.stormino.savonarola.moderation.DecisionRepository;
import com.github.stormino.savonarola.moderation.DecisionStatus;
import com.github.stormino.savonarola.moderation.PipelineMetrics;
import com.github.stormino.savonarola.settings.SettingsService;
import com.github.stormino.savonarola.store.StoredMessageRepository;
import com.github.stormino.savonarola.telegram.AdminCommand;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import com.github.stormino.savonarola.telegram.Html;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** SPEC 13 — purely on demand; nothing here is ever posted on a schedule. */
@Component
@RequiredArgsConstructor
public class StatsCommand implements AdminCommand {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private final DecisionRepository decisions;
    private final StoredMessageRepository messages;
    private final PipelineMetrics metrics;
    private final LlmUsageTracker usage;
    private final HealthMonitor health;
    private final SettingsService settings;
    private final SavonarolaProperties props;
    private final AdminNotifier notifier;

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public String usage() {
        return "/stats [today|week|month|all]";
    }

    @Override
    public void handle(Message msg, List<String> args) {
        String period = args.isEmpty() ? "today" : args.get(0).toLowerCase();
        Instant since = startOf(period);

        List<Decision> inPeriod = decisions.findByCreatedAtAfter(since);

        StringBuilder sb = new StringBuilder("📊 <b>Statistiche — ").append(period).append("</b>\n");
        sb.append("Modalità: <b>").append(settings.operatingMode())
          .append("</b>, soglia ").append(String.format("%.2f", settings.confidenceThreshold()))
          .append('\n');

        volume(sb, since, inPeriod);
        actions(sb, inPeriod);
        llmUsage(sb);
        health(sb);

        notifier.reply(msg, sb.toString());
    }

    private void volume(StringBuilder sb, Instant since, List<Decision> inPeriod) {
        sb.append("\n<b>Volume</b>\n")
          .append("Messaggi salvati: ")
          .append(messages.countByChatIdAndSentAtAfter(props.telegram().mainChatId(), since))
          .append('\n');

        Map<String, Integer> byRule = new TreeMap<>();
        for (Decision decision : inPeriod) {
            if (decision.getRuleId() != null) {
                byRule.merge(decision.getRuleId(), 1, Integer::sum);
            }
        }
        sb.append("Decisioni: ").append(inPeriod.size()).append('\n');
        byRule.forEach((rule, count) ->
                sb.append("  <code>").append(Html.escape(rule)).append("</code>: ")
                  .append(count).append('\n'));

        sb.append("Finestre valutate: ").append(metrics.windows())
          .append(" su ").append(metrics.judged()).append(" messaggi (dall'avvio)\n")
          .append("Query estesa: ")
          .append(String.format("%.1f%%", metrics.extendedHistoryRate() * 100))
          .append(" delle finestre\n");
    }

    private void actions(StringBuilder sb, List<Decision> inPeriod) {
        Map<Integer, Integer> byRung = new TreeMap<>();
        int executed = 0;
        int dismissed = 0;
        int modified = 0;
        int pending = 0;

        for (Decision decision : inPeriod) {
            switch (decision.getStatus()) {
                case EXECUTED -> {
                    executed++;
                    byRung.merge(decision.getSuggestedRung(), 1, Integer::sum);
                    if (decision.wasModified()) modified++;
                }
                case DISMISSED -> dismissed++;
                case PENDING -> pending++;
                case LOGGED -> { }
            }
        }

        sb.append("\n<b>Azioni</b>\n")
          .append("Eseguite: ").append(executed)
          .append(" (di cui modificate dagli admin: ").append(modified).append(")\n")
          .append("Scartate: ").append(dismissed).append('\n')
          .append("In attesa: ").append(pending).append('\n');

        if (!byRung.isEmpty()) {
            sb.append("Mute per gradino:\n");
            byRung.forEach((rung, count) ->
                    sb.append("  gradino ").append(rung).append(": ").append(count).append('\n'));
        }
    }

    private void llmUsage(StringBuilder sb) {
        sb.append("\n<b>Consumo LLM</b> (dall'avvio)\n");
        Map<LlmUsageTracker.Key, LlmUsageTracker.Usage> snapshot = usage.snapshot();
        if (snapshot.isEmpty()) {
            sb.append("Nessuna chiamata.\n");
            return;
        }
        snapshot.forEach((key, value) ->
                sb.append("  ").append(Html.escape(key.model())).append(" / ").append(key.callType())
                  .append(": ").append(value.calls()).append(" chiamate, ")
                  .append(value.totalTokens()).append(" token\n"));
    }

    private void health(StringBuilder sb) {
        sb.append("\n<b>Salute</b>\n")
          .append("Stato: ").append(health.isDegraded() ? "⚠️ degradato" : "ok").append('\n')
          .append("Errori consecutivi: ").append(health.getConsecutiveFailures()).append('\n')
          .append("Fallback su modello secondario: ").append(health.getFallbacks()).append('\n')
          .append("Ultimo successo: ")
          .append(Duration.between(health.getLastSuccess(), Instant.now()).toMinutes())
          .append(" minuti fa\n");
        if (health.getLastError() != null) {
            sb.append("Ultimo errore: ").append(Html.escape(health.getLastError())).append('\n');
        }
    }

    private static Instant startOf(String period) {
        return switch (period) {
            case "today" -> LocalDate.now(ROME).atStartOfDay(ROME).toInstant();
            case "week" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "month" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "all" -> Instant.EPOCH;
            default -> throw new IllegalArgumentException(
                    "Periodo non riconosciuto: '" + period + "'. Usa today, week, month o all.");
        };
    }
}
