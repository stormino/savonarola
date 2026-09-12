package com.github.stormino.savonarola.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    private long userId;

    private String displayName;

    @Column(length = 1024)
    private String typicalTone;

    private Instant lastUpdated;

    protected UserProfile() {}

    public UserProfile(long userId, String displayName, String typicalTone) {
        this.userId = userId;
        this.displayName = displayName;
        this.typicalTone = typicalTone;
        this.lastUpdated = Instant.now();
    }

    public void refresh(String typicalTone, String displayName) {
        this.typicalTone = typicalTone;
        this.displayName = displayName;
        this.lastUpdated = Instant.now();
    }

    public long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getTypicalTone() { return typicalTone; }
    public Instant getLastUpdated() { return lastUpdated; }
}
