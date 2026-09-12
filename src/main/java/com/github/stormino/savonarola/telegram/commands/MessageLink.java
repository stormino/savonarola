package com.github.stormino.savonarola.telegram.commands;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A t.me message link, as pasted by an admin into /train.
 *
 * Private supergroups produce https://t.me/c/<internalId>/<messageId>, where the real
 * chat id is the internal id prefixed with -100. Forum topics add a thread segment
 * (/c/<internalId>/<threadId>/<messageId>), so the message id is always the last
 * segment. Public links (https://t.me/<username>/<messageId>) carry a username the bot
 * API will not resolve to a chat id, so chatId stays empty and the caller substitutes
 * the configured main chat — the only chat whose messages are stored anyway.
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

    /** The chat this link points at, falling back to the main group for public links. */
    public long resolveChatId(long mainChatId) {
        return chatId != null ? chatId : mainChatId;
    }
}
