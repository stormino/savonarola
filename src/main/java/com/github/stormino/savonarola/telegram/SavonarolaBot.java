package com.github.stormino.savonarola.telegram;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.moderation.ModerationPipeline;
import com.github.stormino.savonarola.store.MessageStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Component
@Slf4j
@RequiredArgsConstructor
public class SavonarolaBot implements LongPollingSingleThreadUpdateConsumer {

    private final SavonarolaProperties props;
    private final AdminRegistry adminRegistry;
    private final MessageStoreService messageStore;
    private final ModerationPipeline pipeline;
    private final CommandDispatcher commands;

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) return;
        Message msg = update.getMessage();
        if (!msg.hasText() || msg.getFrom() == null || msg.getFrom().getIsBot()) return;

        long chatId = msg.getChatId();

        if (chatId == props.telegram().adminChatId()) {
            if (msg.getText().startsWith("/")) {
                commands.dispatch(msg);
            }
            return;
        }

        if (chatId != props.telegram().mainChatId()) return;

        messageStore.save(msg);

        // Admins are never sanctionable — checked before any LLM call is made.
        if (adminRegistry.isAdmin(chatId, msg.getFrom().getId())) return;

        // No commands are accepted in the main chat: the bot stays invisible there.
        if (msg.getText().startsWith("/")) return;

        pipeline.process(msg);
    }
}
