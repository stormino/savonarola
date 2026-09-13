package com.github.stormino.savonarola.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Admin status is resolved dynamically against Telegram, never from config.
 * A removed admin becomes sanctionable automatically; a new one is exempt immediately.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdminRegistry {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final TelegramClient client;
    private final Map<Long, CachedAdmins> cache = new ConcurrentHashMap<>();

    public boolean isAdmin(long chatId, long userId) {
        return admins(chatId).contains(userId);
    }

    public Set<Long> admins(long chatId) {
        CachedAdmins cached = cache.get(chatId);
        if (cached != null && cached.fetchedAt().plus(TTL).isAfter(Instant.now())) {
            return cached.ids();
        }
        try {
            Set<Long> ids = client.execute(new GetChatAdministrators(String.valueOf(chatId)))
                    .stream()
                    .map(m -> m.getUser().getId())
                    .collect(Collectors.toSet());
            cache.put(chatId, new CachedAdmins(ids, Instant.now()));
            return ids;
        } catch (TelegramApiException e) {
            // Fail closed toward the last known list: never sanction on a transient API error.
            log.warn("Could not refresh admins for chat {}: {}", chatId, e.getMessage());
            return cached != null ? cached.ids() : Set.of();
        }
    }

    private record CachedAdmins(Set<Long> ids, Instant fetchedAt) {}
}
