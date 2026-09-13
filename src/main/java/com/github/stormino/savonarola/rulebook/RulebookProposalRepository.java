package com.github.stormino.savonarola.rulebook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RulebookProposalRepository extends JpaRepository<RulebookProposal, UUID> {

    List<RulebookProposal> findByStatusOrderByCreatedAtDesc(ProposalStatus status);
}
