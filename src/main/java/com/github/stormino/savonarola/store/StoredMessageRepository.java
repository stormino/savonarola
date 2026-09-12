package com.github.stormino.savonarola.store;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoredMessageRepository extends JpaRepository<StoredMessage, Long> {

    Optional<StoredMessage> findByChatIdAndMessageId(long chatId, long messageId);

    /** Context window: most recent messages in the chat, newest first. */
    List<StoredMessage> findByChatIdOrderBySentAtDesc(long chatId, Pageable pageable);

    /** Extended history for pattern detection: what sender said toward target. */
    @Query("""
            select m from StoredMessage m
            where m.chatId = :chatId
              and m.senderId = :senderId
              and m.replyToUserId = :targetId
              and m.sentAt > :since
            order by m.sentAt desc
            """)
    List<StoredMessage> findInteractions(@Param("chatId") long chatId,
                                         @Param("senderId") long senderId,
                                         @Param("targetId") long targetId,
                                         @Param("since") Instant since);

    List<StoredMessage> findByChatIdAndSenderIdAndSentAtAfterOrderBySentAtDesc(
            long chatId, long senderId, Instant since, Pageable pageable);

    /** Resolves a @username an admin typed back to the user id the bot last saw it on. */
    Optional<StoredMessage> findFirstBySenderNameOrderBySentAtDesc(String senderName);

    /** Busiest senders first, so a capped batch run profiles the people who matter most. */
    @Query("""
            select m.senderId
            from StoredMessage m
            where m.chatId = :chatId and m.sentAt > :since
            group by m.senderId
            having count(m) >= :minMessages
            order by count(m) desc
            """)
    List<Long> findActiveSenders(@Param("chatId") long chatId,
                                 @Param("since") Instant since,
                                 @Param("minMessages") long minMessages,
                                 Pageable pageable);

    /** Directed pairs that interact often enough to be worth spending an LLM call on. */
    @Query("""
            select m.senderId as senderId, m.replyToUserId as targetId, count(m) as interactions
            from StoredMessage m
            where m.chatId = :chatId and m.sentAt > :since
              and m.replyToUserId is not null and m.replyToUserId <> m.senderId
            group by m.senderId, m.replyToUserId
            having count(m) >= :minInteractions
            order by count(m) desc
            """)
    List<PairCount> findFrequentPairs(@Param("chatId") long chatId,
                                      @Param("since") Instant since,
                                      @Param("minInteractions") long minInteractions,
                                      Pageable pageable);

    interface PairCount {
        long getSenderId();
        long getTargetId();
        long getInteractions();
    }

    void deleteBySentAtBefore(Instant cutoff);
}
