package com.github.stormino.savonarola.profile;

import jakarta.persistence.*;

import java.time.Instant;

/** How one user habitually behaves toward another — the prior that separates banter from targeting. */
@Entity
@Table(name = "known_dynamics", uniqueConstraints =
        @UniqueConstraint(name = "uq_dynamic_pair", columnNames = {"userId", "withUserId"}))
public class KnownDynamic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private long withUserId;

    @Column(length = 1024)
    private String pattern;

    @Enumerated(EnumType.STRING)
    private DynamicSource source;

    private Instant updatedAt;

    protected KnownDynamic() {}

    public KnownDynamic(long userId, long withUserId, String pattern, DynamicSource source) {
        this.userId = userId;
        this.withUserId = withUserId;
        this.pattern = pattern;
        this.source = source;
        this.updatedAt = Instant.now();
    }

    /**
     * An admin annotation outranks anything the bot inferred and is never overwritten by
     * the batch job: section 7.3 exists precisely because the bot gets these wrong early on.
     */
    public boolean supersededBy(DynamicSource incoming) {
        return source != DynamicSource.ADMIN_ANNOTATED || incoming == DynamicSource.ADMIN_ANNOTATED;
    }

    public void update(String pattern, DynamicSource source) {
        this.pattern = pattern;
        this.source = source;
        this.updatedAt = Instant.now();
    }

    public long getUserId() { return userId; }
    public long getWithUserId() { return withUserId; }
    public String getPattern() { return pattern; }
    public DynamicSource getSource() { return source; }
}
