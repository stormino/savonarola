package com.github.stormino.savonarola.telegram.commands;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DurationParser {

    private static final Pattern SPEC = Pattern.compile("^(\\d+)([mhd])$");

    /** Telegram treats a restriction longer than 366 days as permanent; refuse to get close. */
    private static final int MAX_MINUTES = 30 * 24 * 60;

    private DurationParser() {}

    static int toMinutes(String spec) {
        Matcher m = SPEC.matcher(spec.trim().toLowerCase());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Durata non riconosciuta: '" + spec + "'. Usa per esempio 10m, 2h, 1d.");
        }
        long value = Long.parseLong(m.group(1));
        long minutes = switch (m.group(2)) {
            case "m" -> value;
            case "h" -> value * 60;
            default -> value * 60 * 24;
        };
        if (minutes <= 0) {
            throw new IllegalArgumentException("La durata deve essere maggiore di zero.");
        }
        if (minutes > MAX_MINUTES) {
            throw new IllegalArgumentException(
                    "Durata troppo lunga: il massimo è 30 giorni. Il ban non è mai automatico.");
        }
        return (int) minutes;
    }
}
