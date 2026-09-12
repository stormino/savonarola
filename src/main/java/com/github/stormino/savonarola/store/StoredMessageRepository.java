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

    void deleteBySentAtBefore(Instant cutoff);
}
