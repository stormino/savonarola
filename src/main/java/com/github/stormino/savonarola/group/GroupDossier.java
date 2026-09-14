package com.github.stormino.savonarola.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * What a moderator who had been in the group for years would simply know: running jokes,
 * recurring targets, which names are public figures rather than members.
 *
 * SPEC 20. It exists because the bot flagged an insult against "Puppo" — a journalist the
 * group jokes about — having no way to tell him apart from a member.
 */
@Entity
@Table(name = "group_dossier")
public class GroupDossier {

    @Id
    private long chatId;

    @Column(length = 8192)
    private String notes;

    private Long updatedBy;
    private Instant updatedAt;

    protected GroupDossier() {}

    public GroupDossier(long chatId, String notes, Long updatedBy) {
        this.chatId = chatId;
        this.notes = notes;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public void replace(String notes, Long updatedBy) {
        this.notes = notes;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public void append(String line, Long updatedBy) {
        this.notes = notes == null || notes.isBlank() ? line : notes + "\n" + line;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public long getChatId() { return chatId; }
    public String getNotes() { return notes; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
