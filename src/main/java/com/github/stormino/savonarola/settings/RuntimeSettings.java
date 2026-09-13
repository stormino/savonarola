package com.github.stormino.savonarola.settings;

import com.github.stormino.savonarola.moderation.OperatingMode;
import jakarta.persistence.*;

import java.time.Instant;

/** Single row. A null column means "no override": the value from application.yml stands. */
@Entity
@Table(name = "runtime_settings")
public class RuntimeSettings {

    static final long SINGLETON_ID = 1L;

    @Id
    private long id = SINGLETON_ID;

    @Enumerated(EnumType.STRING)
    private OperatingMode operatingMode;

    private Double confidenceThreshold;

    private Long changedBy;
    private Instant changedAt;

    protected RuntimeSettings() {}

    public void setOperatingMode(OperatingMode mode, long changedBy) {
        this.operatingMode = mode;
        touch(changedBy);
    }

    public void setConfidenceThreshold(double threshold, long changedBy) {
        this.confidenceThreshold = threshold;
        touch(changedBy);
    }

    private void touch(long changedBy) {
        this.changedBy = changedBy;
        this.changedAt = Instant.now();
    }

    public OperatingMode getOperatingMode() { return operatingMode; }
    public Double getConfidenceThreshold() { return confidenceThreshold; }
    public Long getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
