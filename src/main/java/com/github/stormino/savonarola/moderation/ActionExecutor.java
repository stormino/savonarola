package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.telegram.Html;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.ChatPermissions;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Duration;
import java.time.Instant;

/** The only component allowed to write to the main chat. */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActionExecutor {

    private final TelegramClient client;
    private final SavonarolaProperties props;

    public void mute(long chatId, long userId, String displayName, long offendingMessageId, int minutes)
            throws TelegramApiException {
        ChatPermissions muted = ChatPermissions.builder()
                .canSendMessages(false)
                .canSendPolls(false)
                .canSendOtherMessages(false)
                .canAddWebPagePreviews(false)
                .canChangeInfo(false)
                .canInviteUsers(false)
                .canPinMessages(false)
                .build();

        client.execute(RestrictChatMember.builder()
                .chatId(String.valueOf(chatId))
                .userId(userId)
                .permissions(muted)
                .untilDate((int) Instant.now().plus(Duration.ofMinutes(minutes)).getEpochSecond())
                .build());

        announce(chatId, userId, displayName, offendingMessageId, minutes);
    }

    private void announce(long chatId, long userId, String displayName,
                          long offendingMessageId, int minutes) {
        var announcement = props.actionAnnouncement();
        if (!announcement.enabled()) return;

        String text = announcement.template()
                .replace("{user}", mention(userId, displayName))
                .replace("{duration}", humanDuration(minutes));

        var builder = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("HTML");
        if (announcement.replyToOffendingMessage()) {
            builder.replyToMessageId((int) offendingMessageId);
        }
        try {
            client.execute(builder.build());
        } catch (TelegramApiException e) {
            log.error("Mute applied but announcement failed: {}", e.getMessage());
        }
    }

    /**
     * SPEC 11: the announcement mentions the user. A @username is a mention on its own;
     * anyone without one can only be mentioned through a tg://user link, which needs HTML
     * — so the name is escaped, since it is whatever the member set it to.
     */
    private static String mention(long userId, String displayName) {
        if (displayName != null && displayName.startsWith("@")) {
            return Html.escape(displayName);
        }
        String name = displayName == null || displayName.isBlank() ? "l'utente" : displayName;
        return "<a href=\"tg://user?id=" + userId + "\">" + Html.escape(name) + "</a>";
    }

    private static String humanDuration(int minutes) {
        if (minutes < 60) return minutes + " minuti";
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours == 1 ? "1 ora" : hours + " ore";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }
}
