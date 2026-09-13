package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.settings.SettingsService;
import com.github.stormino.savonarola.telegram.AdminCommand;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ThresholdCommand implements AdminCommand {

    private final SettingsService settings;
    private final AdminNotifier notifier;

    @Override
    public String name() {
        return "threshold";
    }

    @Override
    public String usage() {
        return "/threshold [0.0-1.0]";
    }

    @Override
    public void handle(Message msg, List<String> args) {
        if (args.isEmpty()) {
            notifier.reply(msg, "Soglia di confidenza attuale: <b>"
                    + String.format("%.2f", settings.confidenceThreshold()) + "</b>");
            return;
        }

        double requested = parse(args.get(0));
        double previous = settings.confidenceThreshold();
        settings.setConfidenceThreshold(requested, msg.getFrom().getId());

        notifier.send("⚙️ <b>Soglia di confidenza cambiata</b>: "
                + String.format("%.2f", previous) + " → <b>"
                + String.format("%.2f", requested) + "</b>"
                + "\nDa: " + msg.getFrom().getId()
                + "\nPiù bassa: più decisioni proposte e più falsi positivi. "
                + "Più alta: il bot propone solo quando è sicuro.");
    }

    private static double parse(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Soglia non valida: '" + raw + "'. Usa un numero fra 0 e 1.");
        }
    }
}
