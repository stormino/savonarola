package com.github.stormino.savonarola.store;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageStoreService {

    private final StoredMessageRepository repository;
    private final SavonarolaProperties props;

    @Transactional
    public StoredMessage save(Message msg) {
        Long replyToMessageId = null;
        Long replyToUserId = null;
        if (msg.getReplyToMessage() != null) {
            replyToMessageId = (long) msg.getReplyToMessage().getMessageId();
            if (msg.getReplyToMessage().getFrom() != null) {
                replyToUserId = msg.getReplyToMessage().getFrom().getId();
            }
        }
        StoredMessage stored = new StoredMessage(
                msg.getChatId(),
                msg.getMessageId(),
                msg.getFrom().getId(),
                displayName(msg),
                msg.getText(),
                replyToMessageId,
                replyToUserId,
                Instant.ofEpochSecond(msg.getDate()));
        return repository.save(stored);
    }

    /** Newest-first; callers that want chronological order should reverse. */
    public List<StoredMessage> contextWindow(long chatId) {
        return repository.findByChatIdOrderBySentAtDesc(
                chatId, PageRequest.of(0, props.messageStore().contextWindowSize()));
    }

    public List<StoredMessage> interactions(long chatId, long senderId, long targetId, int windowDays) {
        return repository.findInteractions(
                chatId, senderId, targetId, Instant.now().minus(windowDays, ChronoUnit.DAYS));
    }

    public Optional<StoredMessage> find(long chatId, long messageId) {
        return repository.findByChatIdAndMessageId(chatId, messageId);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(props.messageStore().retentionDays(), ChronoUnit.DAYS);
        repository.deleteBySentAtBefore(cutoff);
        log.info("Purged messages older than {}", cutoff);
    }

    private static String displayName(Message msg) {
        var user = msg.getFrom();
        if (user.getUserName() != null) return "@" + user.getUserName();
        return user.getLastName() == null
                ? user.getFirstName()
                : user.getFirstName() + " " + user.getLastName();
    }
}
