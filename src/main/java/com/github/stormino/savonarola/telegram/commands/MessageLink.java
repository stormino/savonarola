package com.github.stormino.savonarola.telegram.commands;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A t.me message link. In /c/ links the real chat id is the path segment prefixed with
 * -100; public links carry a username the bot API will not resolve, so chatId stays
 * empty and the caller substitutes the main chat — the only chat whose messages exist.
 */
public record MessageLink(Long chatId, long messageId) {

    private static final Pattern PRIVATE = Pattern.compile(
            "^(?:https?://)?t\\.me/c/(\\d+)/(?:\\d+/)?(\\d+)/?$");
    private static final Pattern PUBLIC = Pattern.compile(
            "^(?:https?://)?t\\.me/([A-Za-z][A-Za-z0-9_]{3,})/(?:\\d+/)?(\\d+)/?$");

    public static Optional<MessageLink> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String link = raw.trim();

        int query = link.indexOf('?');
        if (query >= 0) link = link.substring(0, query);

        Matcher priv = PRIVATE.matcher(link);
        if (priv.matches()) {
            long internalId = Long.parseLong(priv.group(1));
            return Optional.of(new MessageLink(
                    Long.parseLong("-100" + internalId), Long.parseLong(priv.group(2))));
        }

        Matcher pub = PUBLIC.matcher(link);
        if (pub.matches()) {
            return Optional.of(new MessageLink(null, Long.parseLong(pub.group(2))));
        }

        return Optional.empty();
    }

    public long resolveChatId(long mainChatId) {
        return chatId != null ? chatId : mainChatId;
    }
}
