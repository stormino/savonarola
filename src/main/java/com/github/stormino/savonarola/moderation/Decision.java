package com.github.stormino.savonarola.moderation;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decisions")
public class Decision {

    @Id
    private UUID id;

    private long chatId;
    private long messageId;
    private long subjectUserId;

    private String ruleId;
    private double confidence;

    @Column(length = 2000)
    private String reasoning;

    @Enumerated(EnumType.STRING)
    private ActionType suggestedActionType;
    private int suggestedDurationMinutes;
    private int suggestedRung;

    @Enumerated(EnumType.STRING)
    private ActionType actualActionType;
    private int actualDurationMinutes;

    @Enumerated(EnumType.STRING)
    private DecisionStatus status;

    private Long resolvedBy;
    private Instant createdAt;
    private Instant resolvedAt;

    protected Decision() {}

    public static Decision pending(long chatId, long messageId, long subjectUserId,
                                   Judgment judgment, Action suggested) {
        Decision d = new Decision();
        d.id = UUID.randomUUID();
        d.chatId = chatId;
        d.messageId = messageId;
        d.subjectUserId = subjectUserId;
        d.ruleId = judgment.ruleId();
        d.confidence = judgment.confidence();
        d.reasoning = judgment.reasoning();
        d.suggestedActionType = suggested.type();
        d.suggestedDurationMinutes = suggested.durationMinutes();
        d.suggestedRung = suggested.rung();
        d.status = DecisionStatus.PENDING;
        d.createdAt = Instant.now();
        return d;
    }

    public void markExecuted(Action actual, Long resolvedBy) {
        this.actualActionType = actual.type();
        this.actualDurationMinutes = actual.durationMinutes();
        this.status = DecisionStatus.EXECUTED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
    }

    public void markDismissed(Long resolvedBy) {
        this.status = DecisionStatus.DISMISSED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
    }

    public Action suggestedAction() {
        return new Action(suggestedActionType, suggestedDurationMinutes, suggestedRung);
    }

    public UUID getId() { return id; }
    public long getChatId() { return chatId; }
    public long getMessageId() { return messageId; }
    public long getSubjectUserId() { return subjectUserId; }
    public String getRuleId() { return ruleId; }
    public double getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public DecisionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
