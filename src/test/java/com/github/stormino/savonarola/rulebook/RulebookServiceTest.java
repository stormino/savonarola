package com.github.stormino.savonarola.rulebook;

import com.github.stormino.savonarola.moderation.Severity;
import com.github.stormino.savonarola.rules.ExampleLabel;
import com.github.stormino.savonarola.rules.Rule;
import com.github.stormino.savonarola.rules.RuleExample;
import com.github.stormino.savonarola.rules.RuleExampleRepository;
import com.github.stormino.savonarola.rules.RuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RulebookServiceTest {

    private static final long ADMIN = 42L;

    private RulebookProposalRepository proposals;
    private RuleRepository rules;
    private RuleExampleRepository examples;
    private RulebookService service;

    @BeforeEach
    void setUp() {
        proposals = mock(RulebookProposalRepository.class);
        rules = mock(RuleRepository.class);
        examples = mock(RuleExampleRepository.class);
        service = new RulebookService(proposals, rules, examples);

        when(proposals.save(any())).thenAnswer(call -> call.getArgument(0));
        when(rules.findById(anyString())).thenReturn(Optional.empty());
        when(rules.findByEnabledTrue()).thenReturn(List.of());
        when(examples.existsByRuleIdAndText(anyString(), anyString())).thenReturn(false);
    }

    private static ProposedRule rule(String id, ProposedRule.ProposedExample... examples) {
        return new ProposedRule(id, Severity.HIGH, false, "Definition of " + id, List.of(examples));
    }

    private RulebookProposal proposalOf(ProposedRule... compiled) {
        return service.propose(List.of(compiled), ADMIN);
    }

    @Test
    void storesAProposalWithoutTouchingTheActiveRules() {
        proposalOf(rule("direct_insult"));

        verify(rules, never()).save(any());
        verify(proposals).save(any());
    }

    @Test
    void roundTripsTheProposalItStored() {
        RulebookProposal proposal = proposalOf(
                rule("direct_insult", new ProposedRule.ProposedExample("sei un buffone",
                        ExampleLabel.POSITIVE)));

        List<ProposedRule> read = service.read(proposal);

        assertThat(read).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo("direct_insult");
            assertThat(r.severity()).isEqualTo(Severity.HIGH);
            assertThat(r.examples()).singleElement()
                    .satisfies(e -> assertThat(e.text()).isEqualTo("sei un buffone"));
        });
    }

    @Test
    void addsRulesTheRulebookIntroduces() {
        RulebookProposal proposal = proposalOf(rule("direct_insult"));

        var result = service.approve(proposal, ADMIN);

        ArgumentCaptor<Rule> saved = ArgumentCaptor.forClass(Rule.class);
        verify(rules).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("direct_insult");
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(result.added()).isEqualTo(1);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(proposal.getResolvedBy()).isEqualTo(ADMIN);
    }

    @Test
    void redefinesARuleThatAlreadyExistsInsteadOfDuplicatingIt() {
        Rule existing = new Rule("direct_insult", Severity.LOW, false, "Old definition.", false);
        when(rules.findById("direct_insult")).thenReturn(Optional.of(existing));

        var result = service.approve(proposalOf(rule("direct_insult")), ADMIN);

        verify(rules, never()).save(any());
        assertThat(existing.getDefinition()).isEqualTo("Definition of direct_insult");
        assertThat(existing.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(existing.isEnabled()).isTrue();
        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    void disablesDroppedRulesWithoutDeletingThemOrTheirExamples() {
        Rule dropped = new Rule("no_politics", Severity.LOW, false, "Old rule.", true);
        when(rules.findByEnabledTrue()).thenReturn(List.of(dropped));

        var result = service.approve(proposalOf(rule("direct_insult")), ADMIN);

        assertThat(dropped.isEnabled()).isFalse();
        assertThat(result.disabled()).isEqualTo(1);
        verify(rules, never()).delete(any());
        verify(examples, never()).delete(any());
        verify(examples, never()).deleteAll();
    }

    @Test
    void carriesTheRulebooksOwnExamplesIntoTraining() {
        RulebookProposal proposal = proposalOf(rule("direct_insult",
                new ProposedRule.ProposedExample("sei un buffone", ExampleLabel.POSITIVE)));

        service.approve(proposal, ADMIN);

        ArgumentCaptor<RuleExample> saved = ArgumentCaptor.forClass(RuleExample.class);
        verify(examples).save(saved.capture());
        assertThat(saved.getValue().getText()).isEqualTo("sei un buffone");
        assertThat(saved.getValue().getLabel()).isEqualTo(ExampleLabel.POSITIVE);
    }

    @Test
    void doesNotDuplicateAnExampleOnReapproval() {
        when(examples.existsByRuleIdAndText("direct_insult", "sei un buffone")).thenReturn(true);

        service.approve(proposalOf(rule("direct_insult",
                new ProposedRule.ProposedExample("sei un buffone", ExampleLabel.POSITIVE))), ADMIN);

        verify(examples, never()).save(any());
    }

    @Test
    void rejectionLeavesTheActiveRulebookAlone() {
        RulebookProposal proposal = proposalOf(rule("direct_insult"));

        service.reject(proposal, ADMIN);

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        verify(rules, never()).save(any());
        verify(examples, never()).save(any());
    }
}
