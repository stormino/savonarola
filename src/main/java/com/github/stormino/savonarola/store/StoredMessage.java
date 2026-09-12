package com.github.stormino.savonarola.store;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_chat_message", columnList = "chatId,messageId", unique = true),
        @Index(name = "idx_chat_sent", columnList = "chatId,sentAt"),
        @Index(name = "idx_sender_sent", columnList = "senderId,sentAt")
})
public class StoredMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long chatId;
    private long messageId;
    private long senderId;
    private String senderName;

    @Column(length = 4096)
    private String text;

    private Long replyToMessageId;
    private Long replyToUserId;
    private Instant sentAt;

    protected StoredMessage() {}

    public StoredMessage(long chatId, long messageId, long senderId, String senderName,
                         String text, Long replyToMessageId, Long replyToUserId, Instant sentAt) {
        this.chatId = chatId;
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.replyToMessageId = replyToMessageId;
        this.replyToUserId = replyToUserId;
        this.sentAt = sentAt;
    }

    public long getChatId() { return chatId; }
    public long getMessageId() { return messageId; }
    public long getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getText() { return text; }
    public Long getReplyToMessageId() { return replyToMessageId; }
    public Long getReplyToUserId() { return replyToUserId; }
    public Instant getSentAt() { return sentAt; }
}
