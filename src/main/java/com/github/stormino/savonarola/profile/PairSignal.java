package com.github.stormino.savonarola.profile;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Directional, unlike the symmetric shape sketched in SPEC 7.2: the trigger in 7.4 is
 * negativeInteractionCount(sender, target), and who is aiming at whom is the whole signal.
 */
@Entity
@Table(name = "pair_signals", uniqueConstraints =
        @UniqueConstraint(name = "uq_pair_direction", columnNames = {"senderId", "targetId"}))
public class PairSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long senderId;
    private long targetId;

    private int negativeInteractionCount;

    @Column(length = 1024)
    private String note;

    private Instant updatedAt;

    protected PairSignal() {}

    public PairSignal(long senderId, long targetId, int negativeInteractionCount, String note) {
        this.senderId = senderId;
        this.targetId = targetId;
        this.negativeInteractionCount = negativeInteractionCount;
        this.note = note;
        this.updatedAt = Instant.now();
    }

    public void refresh(int negativeInteractionCount, String note) {
        this.negativeInteractionCount = negativeInteractionCount;
        this.note = note;
        this.updatedAt = Instant.now();
    }

    public long getSenderId() { return senderId; }
    public long getTargetId() { return targetId; }
    public int getNegativeInteractionCount() { return negativeInteractionCount; }
    public String getNote() { return note; }
    public Instant getUpdatedAt() { return updatedAt; }
}
