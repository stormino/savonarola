package com.github.stormino.savonarola.rulebook;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A compiled rulebook waiting for a human (SPEC 2.1). The compiled rules are kept as the
 * raw JSON they were reviewed as, so approving applies exactly what an admin read.
 */
@Entity
@Table(name = "rulebook_proposals")
public class RulebookProposal {

    @Id
    private UUID id;

    @Lob
    @Column(length = 100000)
    private String proposalJson;

    private long proposedBy;

    @Enumerated(EnumType.STRING)
    private ProposalStatus status;

    private Long resolvedBy;
    private Instant createdAt;
    private Instant resolvedAt;

    protected RulebookProposal() {}

    public RulebookProposal(String proposalJson, long proposedBy) {
        this.id = UUID.randomUUID();
        this.proposalJson = proposalJson;
        this.proposedBy = proposedBy;
        this.status = ProposalStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void resolve(ProposalStatus status, long resolvedBy) {
        this.status = status;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getProposalJson() { return proposalJson; }
    public long getProposedBy() { return proposedBy; }
    public ProposalStatus getStatus() { return status; }
    public Long getResolvedBy() { return resolvedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
