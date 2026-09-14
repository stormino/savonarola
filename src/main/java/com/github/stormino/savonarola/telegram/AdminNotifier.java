package com.github.stormino.savonarola.telegram;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
  * Two audiences. The admin chat carries sanctions and system alerts — things someone has
  * to act on. The owner chat, when configured, carries the full judgment stream including
  * the messages that turned out fine, which is useful while calibrating and noise after.
  * The main chat still sees nothing either way.
  */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdminNotifier {

    private final TelegramClient client;
    private final SavonarolaProperties props;

    public void send(String text) {
        sendTo(props.telegram().adminChatId(), text, null);
    }

    /** The verbose stream. Silently dropped when no owner chat is configured. */
    public void sendToOwner(String text) {
        if (!props.telegram().hasOwnerChat()) return;
        sendTo(props.telegram().ownerChatId(), text, null);
    }

    public void sendSystem(String text) {
        send(props.health().tag() + " " + Html.escape(text));
    }

    public void reply(Message msg, String text) {
        sendTo(msg.getChatId(), text, msg.getMessageId());
    }

    private void sendTo(long chatId, String text, Integer replyToMessageId) {
        var builder = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("HTML");
        if (replyToMessageId != null) {
            builder.replyToMessageId(replyToMessageId);
        }
        try {
            client.execute(builder.build());
        } catch (TelegramApiException e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }
}
