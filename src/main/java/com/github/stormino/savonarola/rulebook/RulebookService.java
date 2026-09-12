package com.github.stormino.savonarola.rulebook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.stormino.savonarola.rules.Rule;
import com.github.stormino.savonarola.rules.RuleExample;
import com.github.stormino.savonarola.rules.RuleExampleRepository;
import com.github.stormino.savonarola.rules.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RulebookService {

    private final RulebookProposalRepository proposals;
    private final RuleRepository rules;
    private final RuleExampleRepository examples;
    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional
    public RulebookProposal propose(List<ProposedRule> compiled, long proposedBy) {
        try {
            return proposals.save(new RulebookProposal(
                    mapper.writeValueAsString(compiled), proposedBy));
        } catch (Exception e) {
            throw new IllegalStateException("Could not store the compiled rulebook", e);
        }
    }

    public Optional<RulebookProposal> find(UUID id) {
        return proposals.findById(id);
    }

    public List<RulebookProposal> pending() {
        return proposals.findByStatusOrderByCreatedAtDesc(ProposalStatus.PENDING);
    }

    public List<ProposedRule> read(RulebookProposal proposal) {
        try {
            return mapper.readValue(proposal.getProposalJson(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Stored proposal is unreadable", e);
        }
    }

    /**
     * Rules the new rulebook drops are disabled, never deleted, and no example is ever
     * removed: training (SPEC 8) is admin work that a rulebook rewrite must not destroy.
     */
    @Transactional
    public ApplyResult approve(RulebookProposal proposal, long adminId) {
        List<ProposedRule> compiled = read(proposal);
        Set<String> incoming = compiled.stream().map(ProposedRule::id).collect(Collectors.toSet());

        int added = 0;
        int updated = 0;
        for (ProposedRule proposed : compiled) {
            Optional<Rule> existing = rules.findById(proposed.id());
            if (existing.isPresent()) {
                existing.get().redefine(proposed.severity(), proposed.requiresHistory(),
                        proposed.definition());
                existing.get().setEnabled(true);
                updated++;
            } else {
                rules.save(new Rule(proposed.id(), proposed.severity(),
                        proposed.requiresHistory(), proposed.definition(), true));
                added++;
            }
            storeExamples(proposed, adminId);
        }

        int disabled = 0;
        for (Rule rule : rules.findByEnabledTrue()) {
            if (!incoming.contains(rule.getId())) {
                rule.setEnabled(false);
                disabled++;
            }
        }

        proposal.resolve(ProposalStatus.APPROVED, adminId);
        log.info("Rulebook approved by {}: {} added, {} updated, {} disabled",
                adminId, added, updated, disabled);
        return new ApplyResult(added, updated, disabled);
    }

    @Transactional
    public void reject(RulebookProposal proposal, long adminId) {
        proposal.resolve(ProposalStatus.REJECTED, adminId);
    }

    private void storeExamples(ProposedRule proposed, long adminId) {
        for (var example : proposed.examples()) {
            if (examples.existsByRuleIdAndText(proposed.id(), example.text())) continue;
            examples.save(new RuleExample(proposed.id(), example.text(), example.label(),
                    adminId, null, null));
        }
    }

    public record ApplyResult(int added, int updated, int disabled) {}
}
