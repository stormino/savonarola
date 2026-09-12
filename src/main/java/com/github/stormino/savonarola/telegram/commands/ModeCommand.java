package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.settings.SettingsService;
import com.github.stormino.savonarola.telegram.AdminCommand;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** SPEC 12 — the single most consequential command here, so a change is announced to everyone. */
@Component
@RequiredArgsConstructor
public class ModeCommand implements AdminCommand {

    private final SettingsService settings;
    private final AdminNotifier notifier;

    @Override
    public String name() {
        return "mode";
    }

    @Override
    public String usage() {
        return "/mode [" + Arrays.stream(OperatingMode.values())
                .map(Enum::name).collect(Collectors.joining("|")) + "]";
    }

    @Override
    public void handle(Message msg, List<String> args) {
        if (args.isEmpty()) {
            notifier.reply(msg, "Modalità attuale: <b>" + settings.operatingMode() + "</b>");
            return;
        }

        OperatingMode requested = parse(args.get(0));
        OperatingMode previous = settings.operatingMode();
        if (requested == previous) {
            notifier.reply(msg, "La modalità è già <b>" + previous + "</b>.");
            return;
        }

        settings.setOperatingMode(requested, msg.getFrom().getId());

        notifier.send("⚙️ <b>Modalità operativa cambiata</b>: " + previous + " → <b>"
                + requested + "</b>\nDa: " + msg.getFrom().getId()
                + describe(requested));
    }

    private static String describe(OperatingMode mode) {
        return switch (mode) {
            case LOG_ONLY -> "\nIl bot torna a non poter agire, nemmeno su comando.";
            case ON_DEMAND_ACTION -> "\nIl bot propone le pene; nulla parte senza /execute.";
            case LIVE_ACTION -> "\nIl bot ora esegue da solo sopra soglia di confidenza.";
        };
    }

    private static OperatingMode parse(String raw) {
        try {
            return OperatingMode.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Modalità non riconosciuta: '" + raw + "'.");
        }
    }
}
